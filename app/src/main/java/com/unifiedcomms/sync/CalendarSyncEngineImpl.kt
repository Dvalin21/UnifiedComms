package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Calendar
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventColor
import com.unifiedcomms.data.model.EventReminder
import com.unifiedcomms.data.model.EventStatus
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.security.CryptoManager
import com.unifiedcomms.util.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeBodyPart

/**
 * Resolve a server event color without destroying a known event-specific color.
 * A collection color is a fallback only; an explicit local/user color wins when
 * the server resource has no per-event COLOR property.
 */
internal fun resolveCalendarEventColor(
    parsedColor: EventColor,
    collectionColor: String,
    collectionPath: String,
    existingColor: EventColor?
): EventColor {
    val path = collectionPath.takeIf { it.isNotBlank() }
    if (parsedColor.isExplicit) return parsedColor.copy(calendarId = path)
    if (existingColor != null && existingColor.isExplicit &&
        !sameColor(existingColor.background, collectionColor)
    ) {
        return existingColor.copy(calendarId = path)
    }
    if (collectionColor.isNotBlank()) {
        val normalized = com.unifiedcomms.ui.theme.ColorNormalizer.normalize(collectionColor)
        val rgb = normalized.removePrefix("#").toLongOrNull(16) ?: 0L
        val luminance = 0.299 * ((rgb shr 16) and 0xFF) +
            0.587 * ((rgb shr 8) and 0xFF) +
            0.114 * (rgb and 0xFF)
        return EventColor.fromCalendar(
            collectionColor,
            if (luminance > 150) "#000000" else "#FFFFFF",
            collectionPath
        )
    }
    return existingColor?.copy(calendarId = path) ?: parsedColor.copy(calendarId = path)
}

private fun sameColor(left: String, right: String): Boolean {
    if (right.isBlank()) return false
    val a = com.unifiedcomms.ui.theme.ColorNormalizer.normalize(left)
    val b = com.unifiedcomms.ui.theme.ColorNormalizer.normalize(right)
    return a.isNotEmpty() && a.equals(b, ignoreCase = true)
}

private fun eventIntersectsSyncWindow(event: CalendarEvent, startMs: Long, endMs: Long): Boolean {
    if (event.status == EventStatus.CANCELLED) return false
    if (event.startAtMs in startMs..endMs) return true
    if (event.recurrenceRule == null) return false
    return RecurrenceExpander.expand(event, startMs, endMs).any {
        it.status != EventStatus.CANCELLED && it.startAtMs in startMs..endMs
    }
}

class CalendarSyncEngineImpl(
    private val calendarRepo: CalendarRepository,
    private val accountRepo: AccountRepository,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope
) : CalendarSyncEngine {

    private val _syncProgress = MutableStateFlow<Map<String, SyncProgress>>(emptyMap())
    override val syncProgress: StateFlow<Map<String, SyncProgress>> = _syncProgress

    override suspend fun syncAccount(account: Account): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                updateProgress(account.id, null, SyncStage.CONNECTING, 0, 0)
                val url = account.serverConfig.caldavUrl ?: return@withContext SyncResult.failure("Missing CalDAV URL")
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
                val calDav = newCalDav(url, auth, client)
                val defaultReminderMinutes = runCatching {
                    PreferencesManager.getInstance().getDefaultReminderMinutes()
                }.getOrDefault(60)
                val defaultReminders = listOf(EventReminder.Default(defaultReminderMinutes))

                val allCalendars = calDav.discoverCalendars()
                updateProgress(account.id, null, SyncStage.LISTING_FOLDERS, 0, allCalendars.size)
                if (allCalendars.isEmpty()) {
                    Log.w("CalendarSyncEngineImpl", "No calendars discovered for ${account.email}")
                    return@withContext SyncResult.failure("No calendars discovered (check CalDAV URL / app-password)")
                }

                val syncNow = System.currentTimeMillis()
                val syncStart = syncNow - account.syncConfig.syncPastDays.coerceAtLeast(0).toLong() * 86_400_000L
                val syncEnd = syncNow + account.syncConfig.syncFutureDays.coerceAtLeast(0).toLong() * 86_400_000L
                Log.i(
                    "CalendarSyncEngineImpl",
                    "calendar sync window pastDays=${account.syncConfig.syncPastDays} futureDays=${account.syncConfig.syncFutureDays}"
                )

                // ponytail: persist discovered calendars into the local `calendars`
                // table. Without this the table stays EMPTY, and any code path that
                // needs a calendar row (e.g. invite Accept/Add-to-Calendar, which
                // calls getCalendarsByAccount().first()) silently no-ops because
                // firstOrNull() returns null. Discovered calendars are the source
                // of truth for the local cache; upsert idempotently by serverId
                // (the collection path) so re-syncs don't duplicate.
                for (cal in allCalendars) {
                    val existing = calendarRepo.getCalendarByServerId(account.id, cal.path)
                    if (existing == null) {
                        calendarRepo.insertCalendar(
                            Calendar(
                                accountId = account.id,
                                serverId = cal.path,
                                name = cal.displayName.ifBlank { "Calendar" },
                                color = if (cal.color.isNotBlank()) EventColor.fromCalendar(cal.color, "#FFFFFF", cal.path) else EventColor.Default(),
                                isPrimary = allCalendars.indexOf(cal) == 0,
                                isSelected = true,
                                timeZone = java.time.ZoneId.systemDefault().id
                            )
                        )
                    }
                }

                var localEvents = calendarRepo.getAllEventsForAccount(account.id).first().toMutableList()
                val pushedHrefs = mutableSetOf<String>()

                // Push offline edits before down-sync. Otherwise a server copy fetched
                // later in this same pass overwrites the user's pending local change.
                for (pending in localEvents.filter { it.isLocalOnly || it.needsSync }) {
                    val calendarPath = if (isDavCollectionPath(pending.calendarId)) {
                        pending.calendarId
                    } else {
                        calendarRepo.getCalendarById(pending.calendarId)?.serverId
                            ?: allCalendars.firstOrNull()?.path
                    }
                    if (calendarPath.isNullOrBlank()) continue
                    val pushEvent = pending.copy(calendarId = calendarPath)
                    val href = VEventSerializer.hrefFor(pushEvent)
                    val etag = calDav.putResource(href, VEventSerializer.toVevent(pushEvent), ifMatch = pushEvent.etag)
                        ?: return@withContext SyncResult.failure("Calendar write failed for ${pending.uid}")
                    val stored = pushEvent.copy(
                        serverHref = href,
                        etag = etag,
                        isLocalOnly = false,
                        needsSync = false
                    )
                    calendarRepo.updateEvent(stored)
                    val index = localEvents.indexOfFirst { it.id == stored.id }
                    if (index >= 0) localEvents[index] = stored else localEvents += stored
                    pushedHrefs += pathOf(href)
                }

                // Key the local cache by the exact server href when available. A
                // UID-derived path is only a fallback for local/legacy rows.
                val localEventByPath = localEvents.associateBy { pathOf(VEventSerializer.hrefFor(it)) }

                val masterServerPaths = mutableSetOf<String>()
                var eventsImported = 0
                var itemFailures = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()

                for (cal in allCalendars) {
                    val etagEntries = calDav.getEventETagList(cal.path, syncStart, syncEnd).getOrElse {
                        return@withContext SyncResult.failure(it.message ?: "Calendar collection listing failed")
                    }
                    masterServerPaths.addAll(etagEntries.map { localEt -> pathOf(localEt.href) })
                    val toFetch = etagEntries.filter { entry ->
                        val path = pathOf(entry.href)
                        val local = localEventByPath[path]
                        // Re-fetch when new or changed, except for a resource
                        // pushed earlier in this pass: a missing ETag response
                        // must not restore stale server content.
                        ((local == null || local.etag != entry.etag ||
                            (local.color.calendarId != cal.path && cal.color.isNotBlank())) && path !in pushedHrefs)
                    }.map { it.href }

                    coroutineScope {
                        toFetch.chunked(6).forEach { batch ->
                            val fetched = batch.map { href -> async { calDav.fetchItem(account.id, href) } }.awaitAll()
                            for (res in fetched) {
                                if (res == null) {
                                    itemFailures++
                                    continue
                                }
                                val parsed = ICalParser.parse(res.ical, account.id, cal.path, res.href, defaultColor = cal.color)
                                for (event in parsed.events) {
                                    val existing = localEventByPath[pathOf(res.href)]
                                        ?: calendarRepo.getEventByUid(event.uid, account.id)
                                    val resolvedColor = resolveCalendarEventColor(
                                        parsedColor = event.color,
                                        collectionColor = cal.color,
                                        collectionPath = cal.path,
                                        existingColor = existing?.color
                                    )
                                    val entry = etagEntries.firstOrNull { pathOf(it.href) == pathOf(res.href) }
                                    val updated = event.copy(
                                        id = existing?.id ?: event.id,
                                        calendarId = cal.path,
                                        serverHref = res.href,
                                        etag = res.etag.takeIf { it.isNotBlank() } ?: entry?.etag.orEmpty(),
                                        color = resolvedColor,

                                         attendees = if (existing?.needsSync == true || existing?.isLocalOnly == true) {
                                             existing.attendees
                                         } else {
                                             event.attendees
                                         },
                                         reminders = existing?.reminders ?: defaultReminders

                                    )
                                    if (existing == null) {
                                        calendarRepo.insertEvent(updated)
                                        newItems.add(updated.id)
                                    } else {
                                        calendarRepo.updateEvent(updated)
                                        updatedItems.add(updated.id)
                                    }
                                    val localIndex = localEvents.indexOfFirst { it.id == updated.id }
                                    if (localIndex >= 0) localEvents[localIndex] = updated else localEvents += updated
                                    eventsImported++
                                }
                            }
                        }
                    }
                }

                if (itemFailures > 0) {
                    updateProgress(account.id, null, SyncStage.ERROR, eventsImported, eventsImported)
                    return@withContext SyncResult.failure("$itemFailures calendar items could not be fetched", itemFailures)
                }

                for (event in localEvents) {
                    // Never delete a local-only event, and never delete an event that
                    // was just successfully pushed before the listing snapshot. The
                    // filtered REPORT is authoritative only inside the sync window;
                    // retain older local history rather than deleting it just because
                    // it was outside this request.
                    if (event.isLocalOnly || !eventIntersectsSyncWindow(event, syncStart, syncEnd)) continue
                    val expectedHref = pathOf(VEventSerializer.hrefFor(event))
                    if (expectedHref.isNotBlank() && expectedHref !in pushedHrefs &&
                        masterServerPaths.none { pathOf(it) == expectedHref }
                    ) {
                        calendarRepo.deleteEvent(event)
                    }
                }

                updateProgress(account.id, null, SyncStage.COMPLETED, eventsImported, eventsImported)
                SyncResult.success(eventsImported, newItems, updatedItems)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CalendarSyncEngineImpl", "sync failed for ${account.email}: ${e.message}", e)
                updateProgress(account.id, null, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Calendar sync failed")
            }
        }
    }

    override suspend fun syncCalendar(account: Account, calendar: Calendar): SyncResult {
        // ponytail: this per-calendar variant was a no-op stub reporting COMPLETED.
        // Nothing in the app calls it (SyncManager routes to syncAccount), but leaving a
        // fake-success path in the interface contract is a broken window. Delegate to the
        // real account-wide sync so it does actual work instead of lying.
        return syncAccount(account)
    }

    override suspend fun fetchEvent(account: Account, calendarId: String, uid: String): CalendarEvent? {
        return withContext(Dispatchers.IO) {
            val url = account.serverConfig.caldavUrl ?: return@withContext null
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = newCalDav(url, auth, client)
            val etagEntries = dav.getETagList(calendarId).getOrElse { return@withContext null }
            etagEntries.firstOrNull { entry -> entry.href.contains(uid, true) }?.let { entry ->
                val fetched = dav.fetchItem(account.id, entry.href) ?: return@withContext null
                ICalParser.parse(fetched.ical, account.id, calendarId, entry.href)
                    .events.firstOrNull()
                    ?.copy(etag = entry.etag)
            }
        }
    }

    override suspend fun createEvent(account: Account, event: CalendarEvent): com.unifiedcomms.sync.CreateResult {
        return try {
            if (!isDavCollectionPath(event.calendarId)) {
                return com.unifiedcomms.sync.CreateResult.failure("No calendar collection path")
            }
            val client = clientFor(account)
            val href = VEventSerializer.hrefFor(event)
            val ical = VEventSerializer.toVevent(event)
            val etag = client.putResource(href, ical)
                ?: return com.unifiedcomms.sync.CreateResult.failure("Calendar server write failed")
            // Persist the exact href used for the PUT so reconciliation and later
            // updates do not assume that the server renamed the resource to UID.ics.
            val stored = event.copy(
                serverHref = href,
                etag = etag,
                needsSync = false,
                isLocalOnly = false
            )
            calendarRepo.insertEvent(stored)
            com.unifiedcomms.sync.CreateResult.success(stored.id, stored.uid, etag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            com.unifiedcomms.sync.CreateResult.failure(e.message ?: "Calendar create failed")
        }
    }

    override suspend fun updateEvent(account: Account, event: CalendarEvent): SyncResult {
        return try {
            val stored = calendarRepo.getEventById(event.id)
                ?: calendarRepo.getEventByUid(event.uid, account.id)
            val candidate = if (stored == null) event else event.copy(
                id = stored.id,
                calendarId = if (isDavCollectionPath(event.calendarId)) event.calendarId else stored.calendarId,
                serverHref = event.serverHref ?: stored.serverHref,
                etag = event.etag ?: stored.etag
            )
            if (!isDavCollectionPath(candidate.calendarId)) {
                return SyncResult.failure("No calendar collection path")
            }
            val client = clientFor(account)
            val href = VEventSerializer.hrefFor(candidate)
            val ical = VEventSerializer.toVevent(candidate)
            val etag = client.putResource(href, ical, ifMatch = candidate.etag)
                ?: return SyncResult.failure("Calendar server update failed")
            calendarRepo.updateEvent(
                candidate.copy(
                    serverHref = href,
                    etag = etag,
                    isLocalOnly = false,
                    needsSync = false
                )
            )
            SyncResult.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult.failure(e.message ?: "Calendar update failed")
        }
    }

    override suspend fun deleteEvent(account: Account, calendarId: String, uid: String): SyncResult {
        return try {
            val local = calendarRepo.getEventByUidAndCalendar(uid, account.id, calendarId)
                ?: calendarRepo.getEventByUid(uid, account.id)
            if (local?.isLocalOnly == true) {
                calendarRepo.deleteEvent(local)
                return SyncResult.success()
            }
            val collection = local?.calendarId?.takeIf { isDavCollectionPath(it) } ?: calendarId
            if (!isDavCollectionPath(collection)) return SyncResult.failure("No calendar collection path")
            val href = local?.serverHref?.trim()?.takeIf { it.isNotBlank() }
                ?: "${collection.trimEnd('/')}/$uid.ics"
            if (local == null || !local.isLocalOnly) {
                if (!clientFor(account).deleteResource(href)) {
                    return SyncResult.failure("Calendar server delete failed")
                }
            }
            local?.let { calendarRepo.deleteEvent(it) }
            SyncResult.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult.failure(e.message ?: "Calendar delete failed")
        }
    }

    // ponytail: build a CalDAVClient for a one-off write/delete (mirrors fetchEvent).
    private suspend fun clientFor(account: Account): CalDAVClient {
        val url = account.serverConfig.caldavUrl
            ?: throw IllegalArgumentException("Missing CalDAV URL")
        val auth = crypto.decryptAuthConfig(account.authConfig)
        val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        return newCalDav(url, auth, client)
    }

    override suspend fun respondToInvite(account: Account, eventUid: String, status: com.unifiedcomms.data.model.AttendeeStatus, comment: String?): SyncResult {
        return try {
            val event = calendarRepo.getEventByUid(eventUid, account.id) ?: return SyncResult.failure("Event not found")
            val updatedAttendees = event.attendees.map { att ->
                if (att.email.equals(account.email, ignoreCase = true)) {
                    att.copy(status = status, respondedAt = kotlinx.datetime.Clock.System.now())
                } else att
            }
            val updated = event.copy(
                attendees = updatedAttendees,
                status = when (status) {
                    com.unifiedcomms.data.model.AttendeeStatus.ACCEPTED -> EventStatus.CONFIRMED
                    com.unifiedcomms.data.model.AttendeeStatus.DECLINED -> EventStatus.CANCELLED
                    else -> EventStatus.CONFIRMED
                },
                isCancelled = status == com.unifiedcomms.data.model.AttendeeStatus.DECLINED,
                needsSync = true,
                updatedAt = kotlinx.datetime.Clock.System.now()
            )
            calendarRepo.updateEvent(updated)
            val calDavResult = updateEvent(account, updated)
            if (!calDavResult.success) {
                return calDavResult
            }

            try {
                sendReplyMail(account, updated, status, comment)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CalendarSyncEngineImpl", "RSVP reply email failed for ${event.uid}", e)
                return SyncResult.failure("Calendar updated, but RSVP email failed: ${e.message ?: "SMTP error"}")
            }

            SyncResult.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult.failure(e.message ?: "Response failed")
        }
    }

    // ponytail: RFC 5546 minimal REPLY: message/calendar with one VEVENT, METHOD:REPLY,
    // attendee PARTSTAT updated. Sender = current user; recipient = organizer.
    private suspend fun sendReplyMail(
        account: Account,
        event: CalendarEvent,
        status: com.unifiedcomms.data.model.AttendeeStatus,
        comment: String?
    ) = withContext(Dispatchers.IO) {
        ensureMailMimeHandlers()
        val auth = crypto.decryptAuthConfig(account.authConfig)
        val props = Properties().apply {
            put("mail.smtp.host", account.serverConfig.smtpHost)
            put("mail.smtp.port", account.serverConfig.smtpPort)
            put("mail.smtp.auth", true)
            put("mail.smtp.starttls.enable", account.serverConfig.smtpUseStartTls)
            put("mail.smtp.connectiontimeout", "30000")
            put("mail.smtp.timeout", "30000")
        }
        val session = Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): javax.mail.PasswordAuthentication {
                return javax.mail.PasswordAuthentication(auth.username ?: account.email, auth.passwordEncrypted ?: "")
            }
        })

        val ical = VEventSerializer.toReply(event, account.email, status, comment)
        val multipart = MimeMultipart("mixed")
        multipart.addBodyPart(MimeBodyPart().apply {
            setContent(
                "<p>${event.title}: ${status.name.lowercase()}</p>",
                "text/html; charset=utf-8"
            )
        })
        multipart.addBodyPart(MimeBodyPart().apply {
            setContent(ical, "text/calendar; method=REPLY; charset=UTF-8")
            setHeader("Content-Class", "urn:content-classes:calendarmessage")
        })

        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(account.email))
        val organizer = event.organizer?.takeIf { !it.email.isNullOrBlank() }?.email
            ?: event.attendees.firstOrNull { !it.email.isNullOrBlank() }?.email
        if (organizer.isNullOrBlank()) return@withContext
        msg.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(organizer))
        msg.subject = "Re: ${event.title.takeIf { it.isNotBlank() } ?: "Calendar Invitation"}"
        msg.setHeader("X-Sogo-Message-Type", "calendar:invitation-reply")
        msg.setHeader("Content-Class", "urn:content-classes:calendarmessage")
        msg.setContent(multipart)
        Transport.send(msg)
    }

    private fun escapeIcal(s: String): String = s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")


    fun allProgress() = _syncProgress.map { it.values.toList() }.distinctUntilChanged()

    // ponytail: normalize a DAV href to its URL path, stripping scheme://host so relative
    // (/calendars/x.ics) and absolute (http://host/calendars/x.ics) hrefs compare equal.
    private fun pathOf(href: String): String {
        if (href.isBlank()) return ""
        return runCatching { java.net.URI(href).path }.getOrDefault(href.substringAfterLast('/').let { if (it.contains('.')) "/$it" else it })
    }

    private fun isDavCollectionPath(value: String): Boolean =
        value.startsWith("http://", true) ||
            value.startsWith("https://", true) ||
            value.startsWith("/") ||
            (value.contains('/') && !value.equals("local", ignoreCase = true))

    override fun observeSyncProgress(accountId: String): kotlinx.coroutines.flow.Flow<SyncProgress> {
        return allProgress().map { list -> list.firstOrNull { it.accountId == accountId } ?: SyncProgress(accountId, null, SyncStage.COMPLETED, 0, 0) }
    }

    override suspend fun testConnection(account: Account): com.unifiedcomms.sync.ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val url = account.serverConfig.caldavUrl ?: return@withContext com.unifiedcomms.sync.ConnectionTestResult(false, 0, emptyList(), "Missing CalDAV URL")
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
                // Evidence: discoverCalendars() returns success(0) on empty, which previously made
                // testConnection report calendarOk=true with nothing discoverable. Treat 0 real
                // calendars as a failure so add-account honestly disables the broken CalDAV leg.
                val calendars = newCalDav(url, auth, client).discoverCalendars()
                if (calendars.isEmpty()) {
                    com.unifiedcomms.sync.ConnectionTestResult(false, 0, emptyList(), "No calendars discovered (check CalDAV URL / app-password)")
                } else {
                    com.unifiedcomms.sync.ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("CalDAV"))
                }
            } catch (e: Exception) {
                com.unifiedcomms.sync.ConnectionTestResult(false, 0, emptyList(), e.message)
            }
        }
    }

    override suspend fun getCalendars(account: Account): List<Calendar> = withContext(Dispatchers.IO) {
        val url = account.serverConfig.caldavUrl ?: return@withContext emptyList()
        val auth = crypto.decryptAuthConfig(account.authConfig)
        val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        newCalDav(url, auth, client)
            .discoverCalendars()
            .map { info ->
                Calendar(
                    id = info.path,
                    accountId = account.id,
                    serverId = info.path,
                    name = info.displayName,
                    color = if (info.color.isNotBlank()) EventColor.fromCalendar(info.color, "#FFFFFF", info.path) else EventColor.Default(),
                    isSelected = true,
                    syncEnabled = true,
                    supportedComponents = if (info.supportsVTODO) listOf("VEVENT", "VTODO") else listOf("VEVENT"),
                    lastSyncedAt = kotlinx.datetime.Clock.System.now(),
                    etag = info.ctag
                )
            }
    }

    private fun updateProgress(accountId: String, calendar: String?, stage: SyncStage, current: Int, total: Int) {
        _syncProgress.value = _syncProgress.value + (accountId to SyncProgress(accountId, calendar, stage, current, total))
    }

    // ponytail: build a CalDAVClient, preferring an OAuth bearer token when the account is OAUTH2.
    private fun newCalDav(url: String, auth: com.unifiedcomms.data.model.AuthConfig, client: OkHttpClient): CalDAVClient {
        val bearer = if (auth.type == com.unifiedcomms.data.model.AuthType.OAUTH2) auth.oauthAccessToken else null
        return CalDAVClient(url, auth.username ?: "", auth.passwordEncrypted ?: "", client, bearer)
    }
}
