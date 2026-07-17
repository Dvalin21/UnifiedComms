package com.unifiedcomms

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.model.EventStatus
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlinx.datetime.LocalDateTime

/**
 * E2E verification of the calendar (VEVENT) sync engine's WRITE path against a local
 * mock CalDAV server (dav_mock.py) at http://127.0.0.1:8088/calendars/default/ via
 * `adb reverse tcp:8088 tcp:8088`. Exercises the exact app code path:
 * testConnection -> createEvent (PUT) -> syncAccount (download) -> updateEvent (PUT)
 * -> deleteEvent (DELETE).
 *
 * This proves the VEventSerializer + CalendarSyncEngineImpl create/update/delete changes
 * (commit e9943ed) actually round-trip through CalDAVClient to a real server, closing the
 * "compiles but never uploads" hole. The mock already serves /calendars/default/ as a
 * VEVENT collection (supported-calendar-component-set: VEVENT), so the download path works.
 */
class CalendarSyncE2ETest : kotlinx.coroutines.CoroutineScope {
    override val coroutineContext = kotlinx.coroutines.Dispatchers.IO

    companion object {
        private const val MOCK_PORT = 8088
        // Collection path (relative, matches what a real Calendar.id holds). The base URL
        // 127.0.0.1:8088 is the CalDAV principal; syncAccount discovers /calendars/default/
        // as the collection and normalizes calendarId to this path. hrefFor() then builds
        // /calendars/default/<uid>.ics — matching the mock's PUT/GET/DELETE + download hrefs.
        private const val CALDAV_COLLECTION = "/calendars/default/"
        private const val CALDAV_BASE = "http://127.0.0.1:$MOCK_PORT/"
    }

    private fun freshAccount(): Account {
        val uid = UUID.randomUUID().toString().take(8)
        return Account(
            id = "caldav-$uid",
            name = "Mock CalDAV",
            email = "tester@local",
            accountType = AccountType.GENERIC_CALDAV_CARDDAV,
            serverConfig = ServerConfig(caldavUrl = CALDAV_BASE),
            authConfig = AuthConfig.AppPassword("tester", "secret"),
            syncConfig = SyncConfig.Defaults(),
            uiConfig = UIConfig.Defaults()
        )
    }

    private fun event(uid: String, accountId: String, title: String): CalendarEvent {
        val start = EventDateTime(dateTime = LocalDateTime.parse("2026-08-01T09:00"), timeZone = "America/New_York", isAllDay = false)
        val end = EventDateTime(dateTime = LocalDateTime.parse("2026-08-01T10:00"), timeZone = "America/New_York", isAllDay = false)
        return CalendarEvent(
            accountId = accountId,
            calendarId = CALDAV_COLLECTION,
            uid = uid,
            title = title,
            startAt = start,
            endAt = end,
            status = EventStatus.CONFIRMED
        )
    }

    @Test
    fun fullCalendarSyncRoundTrip(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val engine = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, this)

        val account = freshAccount()
        accountRepo.insert(account)
        val stored = accountRepo.getById(account.id) ?: account

        // 1) testConnection probes the server
        val conn = engine.testConnection(stored)
        assertTrue("testConnection should succeed against mock: ${conn.errorMessage}", conn.success)

        // 2) createEvent PUTs a VEVENT (seed) — engine stores local copy with server etag
        val seedUid = "${account.id}-seed"
        val seed = event(seedUid, account.id, "Seed Event")
        val seedRes = engine.createEvent(stored, seed)
        assertTrue("createEvent (seed) should succeed: ${seedRes.errorMessage}", seedRes.success)
        assertTrue("local seed should carry a server etag", calendarRepo.getEventByUid(seedUid, account.id)?.etag != null)

        // 3) syncAccount downloads -> Room must contain seed (proves GET + getETagList)
        val syncResult = engine.syncAccount(stored)
        assertTrue("syncAccount should succeed: ${syncResult.errorMessage}", syncResult.success)
        assertTrue("downloaded seed should be present in Room", calendarRepo.getEventByUid(seedUid, account.id) != null)

        // 4) createEvent PUTs a second VEVENT (new)
        val newUid = "${account.id}-new"
        val created = engine.createEvent(stored, event(newUid, account.id, "Created Event"))
        assertTrue("createEvent (new) should succeed: ${created.errorMessage}", created.success)

        // 5) re-sync must surface new (proves PUT landed on the server)
        engine.syncAccount(stored)
        assertTrue("created new should be downloadable after PUT (server round-trip)", calendarRepo.getEventByUid(newUid, account.id) != null)

        // 6) updateEvent PUTs the changed VEVENT back to the server
        val updated = event(newUid, account.id, "Created Event (edited)").copy(
            id = calendarRepo.getEventByUid(newUid, account.id)?.id ?: newUid
        )
        val updRes = engine.updateEvent(stored, updated)
        assertTrue("updateEvent should succeed: ${updRes.errorMessage}", updRes.success)

        // 7) re-sync must surface the edited title (proves update PUT landed)
        engine.syncAccount(stored)
        assertTrue("edited title should be downloadable after update PUT",
            calendarRepo.getEventByUid(newUid, account.id)?.title == "Created Event (edited)")

        // 8) deleteEvent removes the VEVENT from the server
        val delResult = engine.deleteEvent(stored, CALDAV_COLLECTION, newUid)
        assertTrue("deleteEvent should succeed: ${delResult.errorMessage}", delResult.success)

        // 9) fetchEvent must return null now (proves DELETE landed on the server)
        engine.syncAccount(stored)
        val fetched = engine.fetchEvent(stored, CALDAV_COLLECTION, newUid)
        assertTrue("deleted new should no longer be fetchable from server", fetched == null)

        accountRepo.delete(account.id)
    }
}
