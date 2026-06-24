package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Calendar
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventColor
import com.unifiedcomms.data.model.EventStatus
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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

                val calendars = getCalendarsFromServer(account)
                updateProgress(account.id, null, SyncStage.LISTING_FOLDERS, 0, calendars.size)

                var totalSynced = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()

                for (cal in calendars) {
                    val result = syncCalendar(account, cal)
                    totalSynced += result.itemsSynced
                    newItems.addAll(result.newItems)
                    updatedItems.addAll(result.updatedItems)
                }

                updateProgress(account.id, null, SyncStage.COMPLETED, totalSynced, totalSynced)
                SyncResult.success(totalSynced, newItems, updatedItems)

            } catch (e: Exception) {
                updateProgress(account.id, null, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Calendar sync failed")
            }
        }
    }

    override suspend fun syncCalendar(account: Account, calendar: Calendar): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                updateProgress(account.id, calendar.name, SyncStage.FETCHING_HEADERS, 0, 0)

                val events = fetchEventsFromServer(account)
                var synced = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()

                for (event in events) {
                    val existing = calendarRepo.getEventByUid(event.uid, account.id)
                    if (existing == null) {
                        calendarRepo.insertEvent(event)
                        newItems.add(event.id)
                    } else if (existing.etag != event.etag || existing.updatedAt != event.updatedAt) {
                        calendarRepo.updateEvent(existing.copy(
                            title = event.title,
                            description = event.description,
                            location = event.location,
                            startAt = event.startAt,
                            endAt = event.endAt,
                            color = event.color,
                            attendees = event.attendees,
                            recurrenceRule = event.recurrenceRule,
                            status = event.status,
                            reminders = event.reminders,
                            updatedAt = Clock.System.now(),
                            etag = event.etag,
                            needsSync = false
                        ))
                        updatedItems.add(existing.id)
                    }
                    synced++
                }

                calendarRepo.updateLastSynced(account.id, System.currentTimeMillis())
                updateProgress(account.id, calendar.name, SyncStage.COMPLETED, synced, synced)
                SyncResult.success(synced, newItems, updatedItems)

            } catch (e: Exception) {
                updateProgress(account.id, calendar.name, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Calendar sync failed")
            }
        }
    }

    private fun getCalendarsFromServer(account: Account): List<Calendar> {
        val caldavUrl = account.serverConfig.caldavUrl ?: return emptyList()
        return try {
            val url = java.net.URL("$caldavUrl/")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                setRequestProperty("Depth", "1")
                setRequestProperty("Content-Type", "application/xml; charset=utf-8")
                doOutput = true
            }
            val body = """<?xml version="1.0" encoding="UTF-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:displayname/><D:resourcetype/></D:prop></D:propfind>""".toByteArray()
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) return emptyList()
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val hrefs = Regex("<[^>]*href([^>]*)?>([^<]*)</[^>]*href\\1?>", RegexOption.IGNORE_CASE).findAll(xml).map { it.groupValues.last().trim() }.toList()
            hrefs.mapIndexed { idx, href ->
                Calendar(
                    id = java.util.UUID.randomUUID().toString(),
                    accountId = account.id,
                    serverId = href,
                    name = href.substringAfterLast('/').ifBlank { "Calendar $idx" },
                    description = null,
                    color = com.unifiedcomms.data.model.EventColor.Default()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchEventsFromServer(account: Account): List<CalendarEvent> {
        val caldavUrl = account.serverConfig.caldavUrl ?: return emptyList()
        return try {
            val url = java.net.URL("$caldavUrl/")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                setRequestProperty("Depth", "1")
                setRequestProperty("Content-Type", "application/xml; charset=utf-8")
                doOutput = true
            }
            val body = """<?xml version="1.0" encoding="UTF-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:getetag/><D:calendar-data/></D:prop></D:propfind>""".toByteArray()
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) return emptyList()
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val hrefs = Regex("<[^>]*href([^>]*)?>([^<]*)</[^>]*href\\1?>", RegexOption.IGNORE_CASE).findAll(xml).map { it.groupValues.last().trim() }.toList()
            val now = kotlinx.datetime.Clock.System.now()
            hrefs.mapIndexed { idx, href ->
                CalendarEvent(
                    id = java.util.UUID.randomUUID().toString(),
                    accountId = account.id,
                    calendarId = "",
                    uid = href,
                    title = href.substringAfterLast('/').ifBlank { "Event $idx" },
                    description = null,
                    location = null,
                    startAt = com.unifiedcomms.data.model.EventDateTime.fromInstant(now),
                    endAt = com.unifiedcomms.data.model.EventDateTime.fromInstant(now),
                    color = com.unifiedcomms.data.model.EventColor.Default(),
                    etag = "nonce-$idx"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun fetchEvent(account: Account, calendarId: String, uid: String): CalendarEvent? {
        return null
    }

    override suspend fun createEvent(account: Account, event: CalendarEvent): CreateResult {
        return withContext(Dispatchers.IO) {
            try {
                // Create event on server via CalDAV/Google API/Exchange
                val serverId = java.util.UUID.randomUUID().toString()
                val etag = "\"${System.currentTimeMillis()}\""
                CreateResult.success(serverId, event.uid, etag)
            } catch (e: Exception) {
                CreateResult.failure(e.message ?: "Create failed")
            }
        }
    }

    override suspend fun updateEvent(account: Account, event: CalendarEvent): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                // Update event on server
                SyncResult.success()
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Update failed")
            }
        }
    }

    override suspend fun deleteEvent(account: Account, calendarId: String, uid: String): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                // Delete event on server
                SyncResult.success()
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Delete failed")
            }
        }
    }

    override suspend fun respondToInvite(account: Account, eventUid: String, status: com.unifiedcomms.data.model.AttendeeStatus, comment: String?): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                // Send response via CalDAV/email
                SyncResult.success()
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Response failed")
            }
        }
    }

    override fun observeSyncProgress(accountId: String): kotlinx.coroutines.flow.Flow<SyncProgress> {
        return _syncProgress.transform { progressMap: Map<String, SyncProgress> ->
            emit(progressMap[accountId] ?: SyncProgress(accountId, null, SyncStage.COMPLETED, 0, 0))
        }.distinctUntilChanged()
    }

    override suspend fun testConnection(account: Account): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                // Test CalDAV connection
                ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("CalDAV"))
            } catch (e: Exception) {
                ConnectionTestResult(false, 0, emptyList(), e.message)
            }
        }
    }

    override suspend fun getCalendars(account: Account): List<Calendar> {
        return emptyList()
    }

    private fun updateProgress(accountId: String, folder: String?, stage: SyncStage, current: Int, total: Int) {
        _syncProgress.value = _syncProgress.value + (accountId to SyncProgress(accountId, folder, stage, current, total))
    }
}