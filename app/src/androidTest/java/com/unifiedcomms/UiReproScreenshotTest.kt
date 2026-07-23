package com.unifiedcomms

import android.app.Application
import androidx.test.core.app.ActivityScenario
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
import org.junit.Test
import java.util.UUID

/**
 * UI REPRO: seed the REAL account + events into Room, launch the real
 * MainActivity (so UnifiedComms is in the foreground), then walk the calendar
 * and email tabs and screenshot them. Proves the UI (not just the data layer)
 * renders email bodies and synced calendar events.
 */
class UiReproScreenshotTest : kotlinx.coroutines.CoroutineScope {
    override val coroutineContext = Dispatchers.IO

    private fun password(): String =
        InstrumentationRegistry.getArguments().getString("password")
            ?: error("Supply live password via: -e password \"...\"")

    private val user = "testbox@houseofmanns.com"
    private val DAV = "https://email.houseofmanns.com/SOGo/dav/"

    @Test
    fun uiRepro(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val calEngine = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, this)
        val emailEngine = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, this)

        // Ensure the lock screen doesn't block the UI walk.
        com.unifiedcomms.util.PreferencesManager.initialize(app)
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", false)

        val acc = Account(
            id = "uirepro-${UUID.randomUUID().toString().take(8)}",
            name = "UI Repro ($user)",
            email = user,
            accountType = AccountType.MAILCOW,
            serverConfig = ServerConfig(
                imapHost = "imap.houseofmanns.com", imapPort = 993, imapUseSsl = true, acceptAllCerts = true,
                smtpHost = "smtp.houseofmanns.com", smtpPort = 587, smtpUseStartTls = true,
                caldavUrl = "${DAV}$user/Calendar/personal/", carddavUrl = "${DAV}$user/Contacts/personal/"
            ),
            authConfig = AuthConfig.AppPassword(user, password()),
            syncConfig = SyncConfig.Defaults(), uiConfig = UIConfig.Defaults()
        )
        accountRepo.insert(acc)
        val stored = accountRepo.getById(acc.id) ?: acc

        // Seed 2 calendar events (this + next month) via the engine.
        val coll = "${DAV}$user/Calendar/personal/"
        val deviceTz = TimeZone.currentSystemDefault().id
        val nowKtx = Clock.System.now().toLocalDateTime(TimeZone.of(deviceTz))
        val endHour = if (nowKtx.hour < 22) nowKtx.hour + 1 else nowKtx.hour
        calEngine.createEvent(stored, CalendarEvent(
            accountId = acc.id, calendarId = coll, uid = "uia-${UUID.randomUUID()}",
            title = "UI Repro Event This Month",
            startAt = EventDateTime(dateTime = nowKtx, timeZone = deviceTz, isAllDay = false),
            endAt = EventDateTime(dateTime = LocalDateTime(nowKtx.year, nowKtx.monthNumber, nowKtx.dayOfMonth, endHour, nowKtx.minute), timeZone = deviceTz, isAllDay = false),
            status = EventStatus.CONFIRMED))
        val nmY = if (nowKtx.monthNumber == 12) nowKtx.year + 1 else nowKtx.year
        val nmM = if (nowKtx.monthNumber == 12) 1 else nowKtx.monthNumber + 1
        calEngine.createEvent(stored, CalendarEvent(
            accountId = acc.id, calendarId = coll, uid = "uib-${UUID.randomUUID()}",
            title = "UI Repro Event Next Month",
            startAt = EventDateTime(dateTime = LocalDateTime(nmY, nmM, 15, 10, 0), timeZone = deviceTz, isAllDay = false),
            endAt = EventDateTime(dateTime = LocalDateTime(nmY, nmM, 15, 11, 0), timeZone = deviceTz, isAllDay = false),
            status = EventStatus.CONFIRMED))
        calEngine.syncAccount(stored)
        emailEngine.syncAccount(stored)

        val calRows = calendarRepo.getUnifiedEvents(listOf(acc.id)).first()
        val emails = emailRepo.getByAccountAndFolder(acc.id, "INBOX", 100, 0).first()
        android.util.Log.e("UIREPRO", "calRows=${calRows.size} emails=${emails.size} firstBodyLen=${emails.firstOrNull()?.bodyText?.length ?: -1}")

        // Launch the REAL app (foreground) and walk the tabs using UiAutomator text
        // selectors (robust to bottom-nav coordinate shifts across builds).
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val sel = androidx.test.uiautomator.UiSelector()
        fun tapText(t: String, wait: Long = 2000) {
            val obj = ui.findObject(sel.text(t))
            if (obj.exists()) obj.click() else android.util.Log.e("UIREPRO", "tapText miss: $t")
            Thread.sleep(wait)
        }
        Thread.sleep(2500)

        // Calendar (month) view.
        tapText("Calendar", 2500)
        ui.executeShellCommand("rm -f /sdcard/uc_calendar.png")
        ui.executeShellCommand("screencap -p /sdcard/uc_calendar.png")
        Thread.sleep(300)

        // Email: go to Inbox, then tap the email card by its body preview text.
        tapText("Inbox", 2500)
        // The unified inbox card shows a body preview ("sync-engine test"), tap it to open.
        val card = ui.findObject(sel.textContains("sync-engine"))
        if (card.exists()) card.click() else android.util.Log.e("UIREPRO", "card miss (preview)")
        Thread.sleep(2800)
        ui.executeShellCommand("rm -f /sdcard/uc_email_detail.png")
        ui.executeShellCommand("screencap -p /sdcard/uc_email_detail.png")
        Thread.sleep(300)

        scenario.close()

        // Biometric lock proof: enable lock, relaunch, capture the (now working) prompt.
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", true)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.startActivity(android.content.Intent(ctx, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        Thread.sleep(2800)
        ui.executeShellCommand("rm -f /sdcard/uc_biometric.png")
        ui.executeShellCommand("screencap -p /sdcard/uc_biometric.png")
        Thread.sleep(300)
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", false)

        accountRepo.delete(acc.id)
    }
}
