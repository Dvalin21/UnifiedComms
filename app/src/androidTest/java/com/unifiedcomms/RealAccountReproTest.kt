package com.unifiedcomms

import android.app.Application
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalDate
import java.util.UUID

/**
 * REPRO: add the REAL houseofmanns account with known-good URLs, run the real
 * sync engines, then read back Room + screenshot the UI. Proves which LAYER is
 * broken for: (1) email body blank, (3) calendar not syncing, (6) biometric lock.
 *
 * Password injected at runtime: -e password "..."
 */
class RealAccountReproTest : kotlinx.coroutines.CoroutineScope {
    override val coroutineContext = Dispatchers.IO

    private fun password(): String =
        InstrumentationRegistry.getArguments().getString("password")
            ?: error("Supply live password via: -e password \"...\"")

    private val user = "testbox@houseofmanns.com"
    private val DAV = "https://email.houseofmanns.com/SOGo/dav/"

    private fun account(): Account {
        val u = user
        return Account(
            id = "repro-${UUID.randomUUID().toString().take(8)}",
            name = "Repro ($u)",
            email = u,
            accountType = AccountType.MAILCOW,
            serverConfig = ServerConfig(
                imapHost = "imap.houseofmanns.com",
                imapPort = 993,
                imapUseSsl = true,
                acceptAllCerts = true,
                smtpHost = "smtp.houseofmanns.com",
                smtpPort = 587,
                smtpUseStartTls = true,
                caldavUrl = "${DAV}$u/Calendar/personal/",
                carddavUrl = "${DAV}$u/Contacts/personal/"
            ),
            authConfig = AuthConfig.AppPassword(u, password()),
            syncConfig = SyncConfig.Defaults(),
            uiConfig = UIConfig.Defaults()
        )
    }

    @Test
    fun reproCalendarAndEmail(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val calEngine = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, this)
        val emailEngine = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, this)

        val acc = account()
        accountRepo.insert(acc)
        val stored = accountRepo.getById(acc.id) ?: acc

        // --- CALENDAR ---
        val calConn = calEngine.testConnection(stored)
        android.util.Log.e("REPRO", "CAL testConnection ok=${calConn.success} err=${calConn.errorMessage}")
        // Seed two real events on the server (testbox has none) so the UI path is
        // exercised: one this month, one next month. Proves createEvent->Room + the
        // full-year view window.
        val coll = "${DAV}$user/Calendar/personal/"
        val deviceTz = TimeZone.currentSystemDefault().id
        val nowKtx = Clock.System.now().toLocalDateTime(TimeZone.of(deviceTz))
        val endHour = if (nowKtx.hour < 22) nowKtx.hour + 1 else nowKtx.hour
        val evA = CalendarEvent(
            accountId = acc.id, calendarId = coll, uid = "reproA-${UUID.randomUUID()}",
            title = "Repro Event This Month",
            startAt = EventDateTime(dateTime = nowKtx, timeZone = deviceTz, isAllDay = false),
            endAt = EventDateTime(dateTime = LocalDateTime(nowKtx.year, nowKtx.monthNumber, nowKtx.dayOfMonth, endHour, nowKtx.minute), timeZone = deviceTz, isAllDay = false),
            status = EventStatus.CONFIRMED
        )
        val nmY = if (nowKtx.monthNumber == 12) nowKtx.year + 1 else nowKtx.year
        val nmM = if (nowKtx.monthNumber == 12) 1 else nowKtx.monthNumber + 1
        val evB = CalendarEvent(
            accountId = acc.id, calendarId = coll, uid = "reproB-${UUID.randomUUID()}",
            title = "Repro Event Next Month",
            startAt = EventDateTime(dateTime = LocalDateTime(nmY, nmM, 15, 10, 0), timeZone = deviceTz, isAllDay = false),
            endAt = EventDateTime(dateTime = LocalDateTime(nmY, nmM, 15, 11, 0), timeZone = deviceTz, isAllDay = false),
            status = EventStatus.CONFIRMED
        )
        android.util.Log.e("REPRO", "CAL createEvent A=${calEngine.createEvent(stored, evA).success} B=${calEngine.createEvent(stored, evB).success}")
        val calSync = calEngine.syncAccount(stored)
        android.util.Log.e("REPRO", "CAL syncAccount ok=${calSync.success} items=${calSync.itemsSynced} err=${calSync.errorMessage}")
        val calRows = calendarRepo.getUnifiedEvents(listOf(acc.id)).first()
        android.util.Log.e("REPRO", "CAL Room rows=${calRows.size} titles=${calRows.map { it.title }}")

        // --- EMAIL ---
        val emConn = emailEngine.testConnection(stored)
        android.util.Log.e("REPRO", "EMAIL testConnection ok=${emConn.success} err=${emConn.errorMessage}")
        val emSync = emailEngine.syncAccount(stored)
        android.util.Log.e("REPRO", "EMAIL syncAccount ok=${emSync.success} items=${emSync.itemsSynced} err=${emSync.errorMessage}")
        val emails = emailRepo.getByAccountAndFolder(acc.id, "INBOX", 100, 0).first()
        android.util.Log.e("REPRO", "EMAIL Room INBOX rows=${emails.size}")
        emails.firstOrNull()?.let { e ->
            android.util.Log.e("REPRO", "EMAIL first subject='${e.subject}' bodyTextLen=${e.bodyText?.length ?: -1} bodyTextHead='${e.bodyText?.take(80)}' bodyHtmlLen=${e.bodyHtml?.length ?: -1}")
        }
        val anyBody = emails.any { !it.bodyText.isNullOrBlank() }
        android.util.Log.e("REPRO", "EMAIL anyBodyNonBlank=$anyBody")

        // --- SCREENSHOT the calendar + email UI (account is in the shared DB) ---
        val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        Thread.sleep(1500)
        ui.click(453, 2171); Thread.sleep(1800) // calendar tab
        ui.executeShellCommand("screencap -p /sdcard/repro_calendar.png")
        Thread.sleep(300)
        ui.click(280, 2171); Thread.sleep(1500) // email tab
        ui.click(540, 700); Thread.sleep(1800) // open first email
        ui.executeShellCommand("screencap -p /sdcard/repro_email_detail.png")
        Thread.sleep(300)

        assertTrue("calendar rows should be >= 2 (seeded + synced, REPRO)", calRows.size >= 2)
        assertTrue("email rows should be > 0 (REPRO)", emails.size > 0)
        assertTrue("at least one email should have a body (REPRO)", anyBody)

        // --- SCREENSHOT biometric lock to prove the fix: enable the lock, restart app,
        //     and capture the (now working) unlock prompt. ---
        com.unifiedcomms.util.PreferencesManager.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val pm = com.unifiedcomms.util.PreferencesManager.getInstance()
        pm.putBoolean("biometric_lock", true)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.startActivity(Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        Thread.sleep(2500)
        ui.executeShellCommand("screencap -p /sdcard/repro_biometric_lock.png")
        Thread.sleep(300)
        pm.putBoolean("biometric_lock", false)

        // best-effort cleanup of seeded events
        runCatching { calEngine.deleteEvent(stored, coll, evA.uid) }
        runCatching { calEngine.deleteEvent(stored, coll, evB.uid) }
        accountRepo.delete(acc.id)
    }
}
