package com.unifiedcomms

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.ContactSource
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.model.EventStatus
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.model.UnifiedContact
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.ContactRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.sync.ContactSyncEngineImpl
import com.unifiedcomms.sync.TaskSyncEngineImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlinx.datetime.LocalDateTime

/**
 * LIVE end-to-end verification of the CalDAV/CardDAV sync engines against the REAL
 * SOGo/mailcow server at email.houseofmanns.com (houseofmanns.com install).
 *
 * This is the "Live E2E" follow-up to the mock-based ContactSyncE2ETest /
 * CalendarSyncE2ETest / TaskSyncE2ETest. Those prove the engine parses + round-trips
 * against a local mock that mirrors real-server quirks. THIS proves the same code
 * path works against the actual provider — real TLS, real SOGo principal walk,
 * real vCard/VEVENT/VTODO PUT/GET/DELETE.
 *
 * Credentials are NEVER hardcoded. Supply them at test runtime:
 *   -e user "...@houseofmanns.com"   (optional; defaults to testbox@houseofmanns.com)
 *   -e password "..."                (REQUIRED — test errors clearly if absent)
 *
 * NOTE: the account LOCKS after >2 wrong passwords in 2 minutes. Enter the correct
 * password. Do not run repeatedly with a bad guess.
 *
 * Run (after ./gradlew :app:assembleDebug :app:assembleAndroidTest + install both APKs
 * on emulator-5556):
 *   adb -s emulator-5556 shell am instrument -w -r \
 *     -e user testbox@houseofmanns.com -e password '...' \
 *     -e class com.unifiedcomms.LiveDavE2ETest \
 *     com.unifiedcomms.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
class LiveDavE2ETest : kotlinx.coroutines.CoroutineScope {
    override val coroutineContext = kotlinx.coroutines.Dispatchers.IO

    companion object {
        // Verified against the live server (2026-07-22): /.well-known/caldav ->
        // 301 -> https://email.houseofmanns.com/SOGo/dav/ ; /SOGo/dav/<user> returns
        // 401 (valid principal). The /dav/SOGo/<user> form is REJECTED (404).
        private const val DAV_BASE = "https://email.houseofmanns.com/SOGo/dav/"
        private const val USER_DEFAULT = "testbox@houseofmanns.com"
    }

    private fun password(): String =
        InstrumentationRegistry.getArguments().getString("password")
            ?: error("Supply the live test password via instrumentation arg: -e password \"...\"")

    private fun user(): String =
        InstrumentationRegistry.getArguments().getString("user") ?: USER_DEFAULT

    private fun liveAccount(): Account {
        val u = user()
        val caldavUrl = "${DAV_BASE}$u/Calendar/personal/"
        val carddavUrl = "${DAV_BASE}$u/Contacts/personal/"
        return Account(
            id = "live-dav-${UUID.randomUUID().toString().take(8)}",
            name = "Live SOGo ($u)",
            email = u,
            accountType = AccountType.MAILCOW,
            serverConfig = ServerConfig(
                caldavUrl = caldavUrl,
                carddavUrl = carddavUrl,
                acceptAllCerts = false
            ),
            authConfig = AuthConfig.AppPassword(u, password()),
            syncConfig = SyncConfig.Defaults(),
            uiConfig = UIConfig.Defaults()
        )
    }

    // ---- CONTACTS (CardDAV) ----
    @Test
    fun liveContactsSync(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val contactRepo = ContactRepositoryImpl(db.contactDao())
        val engine = ContactSyncEngineImpl(contactRepo, accountRepo, crypto, this)

        val account = liveAccount()
        accountRepo.insert(account)
        val stored = accountRepo.getById(account.id) ?: account

        val conn = engine.testConnection(stored)
        assertTrue("CardDAV testConnection failed: ${conn.errorMessage}", conn.success)

        val seedUid = "${account.id}-seed"
        val seed = UnifiedContact(
            id = UUID.randomUUID().toString(),
            displayName = "Live Seed Contact",
            firstName = "Live",
            lastName = "Seed",
            emails = listOf("live-seed@example.com"),
            phoneNumbers = listOf("+15550000100"),
            source = ContactSource.CARDDAV,
            accountId = account.id,
            sourceId = seedUid
        )
        val seedRes = engine.createContact(stored, seed)
        assertTrue("createContact failed: ${seedRes.errorMessage}", seedRes.success)

        val sync = engine.syncAccount(stored)
        assertTrue("contact syncAccount failed: ${sync.errorMessage}", sync.success)
        assertTrue("downloaded seed missing in Room", contactRepo.getBySourceId(account.id, seedUid) != null)

        val del = engine.deleteContact(stored, seedUid)
        assertTrue("deleteContact failed: ${del.errorMessage}", del.success)
        engine.syncAccount(stored)
        assertTrue("deleted seed still fetchable", engine.fetchContact(stored, seedUid) == null)

        accountRepo.delete(account.id)
    }

    // ---- CALENDAR (CalDAV VEVENT) ----
    @Test
    fun liveCalendarSync(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val engine = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, this)

        val account = liveAccount()
        accountRepo.insert(account)
        val stored = accountRepo.getById(account.id) ?: account

        val conn = engine.testConnection(stored)
        assertTrue("CalDAV testConnection failed: ${conn.errorMessage}", conn.success)

        val coll = stored.serverConfig.caldavUrl!!
        val seedUid = "${account.id}-seed"
        val seed = CalendarEvent(
            accountId = account.id,
            calendarId = coll,
            uid = seedUid,
            title = "Live Seed Event",
            startAt = EventDateTime(dateTime = LocalDateTime.parse("2026-09-01T09:00"), timeZone = "UTC", isAllDay = false),
            endAt = EventDateTime(dateTime = LocalDateTime.parse("2026-09-01T10:00"), timeZone = "UTC", isAllDay = false),
            status = EventStatus.CONFIRMED
        )
        val seedRes = engine.createEvent(stored, seed)
        assertTrue("createEvent failed: ${seedRes.errorMessage}", seedRes.success)
        assertTrue("local seed etag missing", calendarRepo.getEventByUid(seedUid, account.id)?.etag != null)

        val sync = engine.syncAccount(stored)
        assertTrue("calendar syncAccount failed: ${sync.errorMessage}", sync.success)
        assertTrue("downloaded seed missing in Room", calendarRepo.getEventByUid(seedUid, account.id) != null)

        val upd = seed.copy(title = "Live Seed Event (edited)",
            id = calendarRepo.getEventByUid(seedUid, account.id)?.id ?: seedUid)
        val updRes = engine.updateEvent(stored, upd)
        assertTrue("updateEvent failed: ${updRes.errorMessage}", updRes.success)
        engine.syncAccount(stored)
        assertTrue("edited title not downloaded",
            calendarRepo.getEventByUid(seedUid, account.id)?.title == "Live Seed Event (edited)")

        val del = engine.deleteEvent(stored, coll, seedUid)
        assertTrue("deleteEvent failed: ${del.errorMessage}", del.success)
        engine.syncAccount(stored)
        assertTrue("deleted seed still fetchable", engine.fetchEvent(stored, coll, seedUid) == null)

        accountRepo.delete(account.id)
    }

    // ---- TASKS (CalDAV VTODO) ----
    @Test
    fun liveTasksSync(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val taskRepo = TaskRepositoryImpl(db.taskDao(), db.taskListDao())
        val engine = TaskSyncEngineImpl(taskRepo, accountRepo, crypto, this)

        val account = liveAccount()
        accountRepo.insert(account)
        val stored = accountRepo.getById(account.id) ?: account

        val conn = engine.testConnection(stored)
        assertTrue("Task DAV testConnection failed: ${conn.errorMessage}", conn.success)

        val calUrl = stored.serverConfig.caldavUrl!!
        val seedUid = "${account.id}-seed"
        val seed = Task(
            uid = seedUid,
            title = "Live Seed Task",
            description = "live dav e2e",
            status = TaskStatus.NEEDS_ACTION,
            accountId = account.id,
            listId = ""
        )
        val seedRes = engine.createTask(stored, seed)
        assertTrue("createTask failed: ${seedRes.errorMessage}", seedRes.success)

        val sync = engine.syncAccount(stored)
        assertTrue("task syncAccount failed: ${sync.errorMessage}", sync.success)
        assertTrue("downloaded task missing in Room", taskRepo.getByUid(seedUid, account.id) != null)

        val del = engine.deleteTask(stored, calUrl, seedUid)
        assertTrue("deleteTask failed: ${del.errorMessage}", del.success)
        engine.syncAccount(stored)
        assertTrue("deleted task still fetchable", engine.fetchTask(stored, calUrl, seedUid) == null)

        accountRepo.delete(account.id)
    }
}
