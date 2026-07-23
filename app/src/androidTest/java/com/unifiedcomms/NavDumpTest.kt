package com.unifiedcomms

import android.app.Application
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.util.UUID

class NavDumpTest : kotlinx.coroutines.CoroutineScope {
    override val coroutineContext = Dispatchers.IO

    private val user = "testbox@houseofmanns.com"
    private val DAV = "https://email.houseofmanns.com/SOGo/dav/"

    @Test
    fun dumpState(): Unit = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val app = ctx.applicationContext as Application
        com.unifiedcomms.util.PreferencesManager.initialize(app)
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", false)
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        // Ensure clean slate so the REAL addAccount() path decides default.
        val dao = db.accountDao()
        val existing = dao.getAllActive().first()
        existing.forEach { runCatching { dao.delete(it) } }

        val dumpDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val sel = UiSelector()

        fun dump(name: String) {
            val f = File(dumpDir, "$name.xml")
            f.delete()
            ui.dumpWindowHierarchy(f.absolutePath)
            android.util.Log.e("NAVDUMP", "=== $name ===")
            listOf("Create Event", "Account Settings", "Default account", "Calendar sync",
                "Contacts sync", "Email sync", "Tasks sync", "July", "Unified Inbox",
                "NavRepro", "Add Account", "Inbox", "testbox").forEach { key ->
                if (f.readText().contains(key)) android.util.Log.e("NAVDUMP", "$name HAS: $key")
            }
        }
        fun tapDesc(d: String, wait: Long = 2000) {
            val o = ui.findObject(sel.description(d))
            if (o.exists()) o.click() else android.util.Log.e("NAVDUMP", "MISS $d")
            Thread.sleep(wait)
        }
        fun tapNavByText(label: String, wait: Long = 2800) {
            val o = ui.findObject(sel.text(label))
            if (o.exists()) o.click() else {
                // fallback: click by desc
                val d = ui.findObject(sel.description(label))
                if (d.exists()) d.click() else android.util.Log.e("NAVDUMP", "MISS NAV $label")
            }
            Thread.sleep(wait)
        }
        // Click a node by content-desc if present, else by its reported bounds (for FAB).
        fun clickBoundsInHierarchy(name: String, pred: (String) -> Boolean) {
            val f = File(dumpDir, "$name.xml")
            val xml = if (f.exists()) f.readText() else ""
            var clicked = false
            for (m in Regex("<node[^>]*?/>").findAll(xml)) {
                val t = m.value
                if (pred(t)) {
                    val b = Regex("bounds=\"\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]\"").find(t)
                    if (b != null) {
                        val (x0, y0, x1, y1) = b.destructured
                        val cx = (x0.toInt() + x1.toInt()) / 2
                        val cy = (y0.toInt() + y1.toInt()) / 2
                        // FAB region: right side, above system nav (y<1740)
                        if (cy < 1740 && cx > 1800 && y0.toInt() > 1450) { ui.click(cx, cy); clicked = true; break }
                    }
                }
            }
            android.util.Log.e("NAVDUMP", if (clicked) "FAB-CLICK ok" else "FAB-CLICK miss")
            Thread.sleep(2600)
        }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(2500)
        dump("inbox")

        // A: drive calendar via real nav (text), then find + click FAB from real hierarchy
        tapNavByText("Calendar")
        dump("cal")
        // FAB = clickable leaf node with no text/desc in app content area (right side)
        clickBoundsInHierarchy("cal") { t ->
            "clickable=\"true\"" in t && "checkable=\"true\"" !in t &&
            Regex("content-desc=\"\"").containsMatchIn(t) && Regex("text=\"\"").containsMatchIn(t)
        }
        dump("fab")   // expect Create Event form

        // back to inbox, then Settings -> account (verify D via REAL addAccount path)
        tapNavByText("Inbox")
        Thread.sleep(800)
        tapDesc("Settings", 2500)
        // Open the seeded account (added below via vm) -> its settings
        // First add the account through the REAL ViewModel addAccount path (exercises default logic)
        val acc = Account(
            id = "nav-fix-${UUID.randomUUID().toString().take(8)}",
            name = "NavFix ($user)", email = user, accountType = AccountType.MAILCOW,
            serverConfig = ServerConfig(
                imapHost = "imap.houseofmanns.com", imapPort = 993, imapUseSsl = true, acceptAllCerts = true,
                smtpHost = "smtp.houseofmanns.com", smtpPort = 587, smtpUseStartTls = true,
                caldavUrl = "${DAV}$user/Calendar/personal/", carddavUrl = "${DAV}$user/Contacts/personal/"
            ),
            authConfig = AuthConfig.AppPassword(user, "repro-dummy"),
            syncConfig = SyncConfig.Defaults().copy(syncCalendar = true, syncContacts = true),
            uiConfig = UIConfig.Defaults()
        )
        // Use the app's MainViewModel instance (exposed as `vm` in MainActivity) to add for real.
        val fld = com.unifiedcomms.ui.main.MainActivity::class.java.getDeclaredField("vm"); fld.isAccessible = true
        // Grab the live activity instance
        var liveVm: com.unifiedcomms.ui.main.MainViewModel? = null
        scenario.onActivity { a -> liveVm = fld.get(a) as com.unifiedcomms.ui.main.MainViewModel }
        liveVm?.let { runBlocking { it.addAccount(acc) } }
        Thread.sleep(1500)
        tapTextContainsSafe(ui, "NavFix", 2500)
        dump("acct")  // Default account should now be ON
        scenario.close()
        runCatching { accountRepo.delete(acc.id) }
    }

    private fun tapTextContainsSafe(ui: UiDevice, t: String, wait: Long) {
        val o = ui.findObject(UiSelector().textContains(t))
        if (o.exists()) o.click() else android.util.Log.e("NAVDUMP", "MISS TXT $t")
        Thread.sleep(wait)
    }
}
