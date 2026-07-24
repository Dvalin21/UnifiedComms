package com.unifiedcomms

import android.app.Application
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.EmailAddress
import com.unifiedcomms.data.model.EmailFlags
import com.unifiedcomms.data.model.EmailRecipients
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ScreenshotTourTest {
    private val user = "testbox@houseofmanns.com"
    private val DAV = "https://email.houseofmanns.com/SOGo/dav/"

    @Test
    fun tour(): Unit = runBlocking {
        val mode = InstrumentationRegistry.getArguments().getString("mode") ?: "light"
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val app = ctx.applicationContext as Application
        com.unifiedcomms.util.PreferencesManager.initialize(app)
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", false)
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = EmailRepositoryImpl(db.emailDao())

        val dao = db.accountDao()
        dao.getAllActive().first().forEach { runCatching { dao.delete(it) } }

        val accId = "tour-${UUID.randomUUID().toString().take(8)}"
        val acc = Account(
            id = accId, name = "HouseOfManns", email = user, accountType = AccountType.MAILCOW,
            serverConfig = ServerConfig(
                imapHost = "imap.houseofmanns.com", imapPort = 993, imapUseSsl = true, acceptAllCerts = true,
                smtpHost = "smtp.houseofmanns.com", smtpPort = 587, smtpUseStartTls = true,
                caldavUrl = "${DAV}$user/Calendar/personal/", carddavUrl = "${DAV}$user/Contacts/personal/"
            ),
            authConfig = AuthConfig.AppPassword(user, "seed-dummy"),
            syncConfig = SyncConfig.Defaults().copy(syncCalendar = true, syncContacts = true),
            uiConfig = UIConfig.Defaults()
        )
        accountRepo.insert(acc)

        val now = Clock.System.now()
        val samples = listOf(
            Triple("UnifiedComms", "UnifiedComms HouseOfManns E2E", "Weekly sync test results look good. All folders replicated."),
            Triple("Test Box", "Collected Address Book", "The shared address book has been created and is now visible."),
            Triple("Alice Morgan", "Q3 Roadmap Review", "Attached the revised roadmap. Please review before Thursday's sync."),
            Triple("Billing", "Invoice #4421 Due", "Your monthly invoice is ready. Amount due 12.00 EUR."),
            Triple("Newsletter", "Weekly Digest", "Top stories this week from the UnifiedComms project."),
            Triple("Support", "Ticket resolved", "Your ticket #8832 has been marked resolved. Reply if needed.")
        )
        samples.forEachIndexed { i, (name, subj, body) ->
            emailRepo.insert(Email(
                accountId = accId, folder = "INBOX", uid = "${i+1}", messageId = "<${(i+1)}@hm>",
                threadId = "t${(i+1)}", sender = EmailAddress(name, "$name.${i}@houseofmanns.com"),
                recipients = EmailRecipients(to = listOf(EmailAddress("Me", user))),
                subject = subj, bodyText = body, preview = body.take(80),
                sentAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (i + 1) * 37 * 60_000L),
                receivedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (i + 1) * 37 * 60_000L),
                flags = if (i % 2 == 0) EmailFlags(isRead = false) else EmailFlags(isRead = true)
            ))
        }

        val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val sel = UiSelector()
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        fun shot(name: String) {
            val f = File(dir, "$name.png")
            f.delete()
            ui.takeScreenshot(f)
            Thread.sleep(700)
            android.util.Log.e("TOUR", "shot $name -> ${f.exists()}")
        }
        fun tapText(t: String, wait: Long = 1800) {
            val o = ui.findObject(sel.text(t))
            if (o.exists()) o.click() else android.util.Log.e("TOUR", "MISS $t")
            Thread.sleep(wait)
        }
        fun tapDesc(d: String, wait: Long = 1800) {
            val o = ui.findObject(sel.description(d))
            if (o.exists()) o.click() else android.util.Log.e("TOUR", "MISSD $d")
            Thread.sleep(wait)
        }
        val nav = mapOf(
            "Inbox" to (306 to 1701), "Calendar" to (893 to 1701), "Tasks" to (1480 to 1701),
            "Chat" to (2067 to 1701), "People" to (2654 to 1701)
        )
        fun go(tab: String, wait: Long = 2200) {
            val (x, y) = nav[tab]!!
            ui.click(x, y); Thread.sleep(wait)
        }
        fun back(wait: Long = 1200) { ui.pressBack(); Thread.sleep(wait) }

        // Set theme BEFORE launching so the activity starts in the right mode (nav works throughout)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            if (mode == "dark") "cmd uimode night yes" else "cmd uimode night no"
        )
        Thread.sleep(1200)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(2500)

        if (mode == "dark") {
            go("Inbox"); shot("uc_10_inbox_dark")
            go("Calendar"); shot("uc_11_calendar_dark"); go("Inbox")
            go("Tasks"); shot("uc_12_tasks_dark"); go("Inbox")
            tapDesc("Settings", 2200); shot("uc_13_settings_dark")
        } else {
            go("Inbox"); shot("uc_01_inbox")
            ui.click(700, 420); Thread.sleep(1800); shot("uc_02_email_detail"); back()
            go("Calendar"); shot("uc_04_calendar")
            ui.click(2894, 1467); Thread.sleep(1800); shot("uc_create_event"); back()
            go("Inbox")
            go("Tasks"); shot("uc_05_tasks"); go("Inbox")
            go("Chat"); shot("uc_06_messages"); go("Inbox")
            go("People"); shot("uc_people"); go("Inbox")
            tapDesc("Settings", 2200); shot("uc_07_settings")
            tapText("Add Account", 2200); shot("uc_08_add_account"); back()
            tapText("HouseOfManns", 2200); shot("uc_account_settings"); back()
            back()
            tapDesc("Search", 1800); shot("uc_09_search"); back()
        }

        scenario.close()
        runCatching { accountRepo.delete(accId) }
    }
}
