package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Calendar
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventStatus
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.w3c.dom.NodeList

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
                updateProgress(account.id, null, SyncStage.COMPLETED, 0, 0)
                SyncResult.success(0, emptyList(), emptyList())
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
            val calDav = account.serverConfig.caldavUrl ?: return@withContext null
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = CalDAVClient(client, calDav, requireNotNull(auth.username), requireNotNull(auth.passwordEncrypted))
            val hrefs = dav.listCalendarItems(calendarId)
            hrefs.firstOrNull { it.contains(uid, true) }?.let { href ->
                val fetched = dav.fetchItem(account, href) ?: return@withContext null
                ICalParser.parse(fetched.first, account.id, calendarId, href).events.firstOrNull()
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

    override fun observeSyncProgress(accountId: String): kotlinx.coroutines.flow.Flow<SyncProgress> {
        return allProgress().map { list -> list.firstOrNull { it.accountId == accountId } ?: SyncProgress(accountId, null, SyncStage.COMPLETED, 0, 0) }
    }

    override suspend fun testConnection(account: Account): com.unifiedcomms.sync.ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val calDav = account.serverConfig.caldavUrl ?: return@withContext com.unifiedcomms.sync.ConnectionTestResult(false, 0, emptyList(), "Missing CalDAV URL")
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
                CalDAVClient(client, calDav, requireNotNull(auth.username), requireNotNull(auth.passwordEncrypted))
                    .listCalendars()
                com.unifiedcomms.sync.ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("CalDAV"))
            } catch (e: Exception) {
                com.unifiedcomms.sync.ConnectionTestResult(false, 0, emptyList(), e.message)
            }
        }
    }

    override suspend fun getCalendars(account: Account): List<Calendar> = emptyList()

    private fun updateProgress(accountId: String, calendar: String?, stage: SyncStage, current: Int, total: Int) {
        _syncProgress.value = _syncProgress.value + (accountId to SyncProgress(accountId, calendar, stage, current, total))
    }
}
