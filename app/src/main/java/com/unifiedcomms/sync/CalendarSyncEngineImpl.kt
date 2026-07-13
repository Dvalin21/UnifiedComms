package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Calendar
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventStatus
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.security.CryptoManager
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
import java.util.concurrent.TimeUnit

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
                val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
                val calDav = newCalDav(url, auth, client)

                val allCalendars = calDav.discoverCalendars()
                updateProgress(account.id, null, SyncStage.LISTING_FOLDERS, 0, allCalendars.size)
                if (allCalendars.isEmpty()) {
                    Log.w("CalendarSyncEngineImpl", "No calendars discovered for ${account.email}")
                    return@withContext SyncResult.success(0, emptyList(), emptyList())
                }

                val localEvents = calendarRepo.getAllEventsForAccount(account.id).first()
                // ponytail: key by uid, not calendarId — a calendar can hold many
                // events, so keying by calendarId collapsed them to a single entry and
                // broke the etag-skip (every sync re-fetched). uid is unique per event.
                val localEventByUid = localEvents.associateBy { it.uid }

                val masterServerHrefs = mutableSetOf<String>()
                var eventsImported = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()

                for (cal in allCalendars) {
                    val etagEntries = calDav.getETagList(cal.path)
                    masterServerHrefs.addAll(etagEntries.map { localEt -> localEt.href })
                    val toFetch = etagEntries.filter { entry ->
                        val local = localEventByUid[entry.uidFromHref()]
                        local == null || local.etag != entry.etag
                    }.map { it.href }

                    coroutineScope {
                        toFetch.chunked(6).forEach { batch ->
                            val fetched = batch.map { href -> async { calDav.fetchItem(account.id, href) } }.awaitAll()
                            for (res in fetched) {
                                if (res == null) continue
                                val parsed = ICalParser.parse(res.ical, account.id, cal.path, res.href)
                                for (event in parsed.events) {
                                    val existing = calendarRepo.getEventByUid(event.uid, account.id)
                                    val updated = event.copy(
                                        id = existing?.id ?: event.id,
                                        // ponytail: calendarId = the collection path (cal.path),
                                        // the same value a locally-created event uses in
                                        // createEvent. Previously this stored the full item href,
                                        // so a local event's calendarId (a path) never matched the
                                        // server href set and got deleted on the next down-sync.
                                        calendarId = cal.path,
                                        etag = res.etag,
                                        color = existing?.color ?: event.color,
                                        attendees = existing?.let { if (it.attendees.isNotEmpty()) it.attendees else event.attendees } ?: event.attendees
                                    )
                                    if (existing == null) {
                                        calendarRepo.insertEvent(updated)
                                        newItems.add(updated.id)
                                    } else {
                                        calendarRepo.updateEvent(updated)
                                        updatedItems.add(updated.id)
                                    }
                                    eventsImported++
                                }
                            }
                        }
                    }
                }

                for (event in localEvents) {
                    // ponytail: never delete locally-created events during a server down-sync.
                    if (event.isLocalOnly) continue
                    // now calendarId is the collection path (cal.path), which matches the
                    // paths in masterServerHrefs — so a real local event is no longer deleted.
                    if (event.calendarId.isNotBlank() && event.calendarId !in masterServerHrefs) {
                        calendarRepo.deleteEvent(event)
                    }
                }

                updateProgress(account.id, null, SyncStage.COMPLETED, eventsImported, eventsImported)
                SyncResult.success(eventsImported, newItems, updatedItems)
            } catch (e: Exception) {
                Log.e("CalendarSyncEngineImpl", "sync failed for ${account.email}: ${e.message}", e)
                updateProgress(account.id, null, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Calendar sync failed")
            }
        }
    }

    override suspend fun syncCalendar(account: Account, calendar: Calendar): SyncResult {
        return withContext(Dispatchers.IO) {
            updateProgress(account.id, calendar.name, SyncStage.COMPLETED, 0, 0)
            SyncResult.success(0, emptyList(), emptyList())
        }
    }

    override suspend fun fetchEvent(account: Account, calendarId: String, uid: String): CalendarEvent? {
        return withContext(Dispatchers.IO) {
            val url = account.serverConfig.caldavUrl ?: return@withContext null
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = newCalDav(url, auth, client)
            val etagEntries = dav.getETagList(calendarId)
            etagEntries.firstOrNull { entry -> entry.href.contains(uid, true) }?.let { entry ->
                val fetched = dav.fetchItem(account.id, entry.href) ?: return@withContext null
                ICalParser.parse(fetched.ical, account.id, calendarId, entry.href).events.firstOrNull()
            }
        }
    }

    override suspend fun createEvent(account: Account, event: CalendarEvent): com.unifiedcomms.sync.CreateResult {
        return try {
            calendarRepo.insertEvent(event)
            com.unifiedcomms.sync.CreateResult.success(event.id, event.uid, event.etag.orEmpty())
        } catch (e: Exception) {
            com.unifiedcomms.sync.CreateResult.failure(e.message ?: "Calendar create failed")
        }
    }

    override suspend fun updateEvent(account: Account, event: CalendarEvent): SyncResult {
        return try {
            calendarRepo.updateEvent(event)
            SyncResult.success()
        } catch (e: Exception) {
            SyncResult.failure(e.message ?: "Calendar update failed")
        }
    }

    override suspend fun deleteEvent(account: Account, calendarId: String, uid: String): SyncResult {
        return try {
            calendarRepo.getEventByUid(uid, account.id)?.let { calendarRepo.deleteEvent(it) }
            SyncResult.success()
        } catch (e: Exception) {
            SyncResult.failure(e.message ?: "Calendar delete failed")
        }
    }

    override suspend fun respondToInvite(account: Account, eventUid: String, status: com.unifiedcomms.data.model.AttendeeStatus, comment: String?): SyncResult {
        return try {
            val event = calendarRepo.getEventByUid(eventUid, account.id) ?: return SyncResult.failure("Event not found")
            val updated = event.copy(status = when (status) {
                com.unifiedcomms.data.model.AttendeeStatus.ACCEPTED -> EventStatus.CONFIRMED
                com.unifiedcomms.data.model.AttendeeStatus.DECLINED -> EventStatus.CANCELLED
                else -> EventStatus.CONFIRMED
            })
            calendarRepo.updateEvent(updated)
            SyncResult.success()
        } catch (e: Exception) {
            SyncResult.failure(e.message ?: "Response failed")
        }
    }

    fun allProgress() = _syncProgress.map { it.values.toList() }.distinctUntilChanged()

    // ponytail: the VEVENT UID is the .ics filename stem in the collection href.
    private fun CalDAVClient.ETagEntry.uidFromHref(): String =
        href.substringAfterLast('/').substringBefore('.')

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
                newCalDav(url, auth, client)
                    .discoverCalendars()
                com.unifiedcomms.sync.ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("CalDAV"))
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
                    color = com.unifiedcomms.data.model.EventColor.Default(),
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
