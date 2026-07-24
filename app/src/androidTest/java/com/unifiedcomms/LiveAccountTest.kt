package com.unifiedcomms

import android.app.Application
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.ContactRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.File
import java.util.UUID
import android.util.Log

class LiveAccountTest {
    @Test
    fun liveAddAndSync(): Unit = runBlocking {
        val pw = InstrumentationRegistry.getArguments().getString("password")
        Log.e("LIVE", "password present=${!pw.isNullOrBlank()}")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val app = ctx.applicationContext as Application
        com.unifiedcomms.util.PreferencesManager.initialize(app)
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", false)
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val calRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val contactRepo = ContactRepositoryImpl(db.contactDao())

        // clean slate (one-shot, no collect hang)
        db.accountDao().getAllActive().first().forEach { runCatching { db.accountDao().delete(it) } }

        val user = "testbox@houseofmanns.com"
        val host = "houseofmanns.com"
        val draft = Account(
            id = "live-${UUID.randomUUID().toString().take(8)}",
            name = "HouseOfManns", email = user, accountType = AccountType.MAILCOW,
            serverConfig = ServerConfig.MailcowDefaults(host, user).copy(acceptAllCerts = true),
            authConfig = AuthConfig.AppPassword(user, pw ?: "MISSING"),
            syncConfig = SyncConfig.Defaults().copy(syncCalendar = true, syncContacts = true, syncTasks = true),
            uiConfig = UIConfig.Defaults()
        )

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(2500)

        var emailOk = false
        var calOk = false
        var conOk = false
        var addOk = false
        var syncRows = -1
        scenario.onActivity { activity ->
            val vm = activity.vm
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    val prov = withTimeout(60_000) { vm.provisionAccount(draft) }
                    emailOk = prov.emailOk
                    calOk = prov.calendarOk
                    conOk = prov.contactsOk
                    Log.e("LIVE", "PROVISION emailOk=$emailOk calOk=$calOk conOk=$conOk emailErr=[${prov.emailError}] calErr=[${prov.calendarError}] conErr=[${prov.contactsError}]")
                    if (emailOk) {
                        vm.addAccount(draft)   // in-process -> app Flow re-emits
                        addOk = true
                        Log.e("LIVE", "ADD done")
                        vm.syncAccountAsync(draft)  // full sync on viewModelScope
                        Log.e("LIVE", "SYNC launched")
                    } else {
                        Log.e("LIVE", "PROVISION email not ok; not adding")
                    }
                } catch (e: Exception) {
                    Log.e("LIVE", "ERR ${e.message}")
                }
            }
        }

        // wait for provision + sync + Room settle
        Thread.sleep(30_000)

        val emailsInbox = runCatching { emailRepo.getByAccountAndFolder(draft.id, "INBOX", 200, 0).first().size }.getOrDefault(-1)
        val allEmails = runCatching { emailRepo.getTotalCount(draft.id).toInt() }.getOrDefault(-1)
        val events = runCatching { calRepo.getAllEventsForAccount(draft.id).first().size }.getOrDefault(-1)
        val contacts = runCatching { contactRepo.getByAccount(draft.id).first().size }.getOrDefault(-1)
        val accounts = runCatching { accountRepo.getAllActive().first().size }.getOrDefault(-1)
        val def = runCatching { accountRepo.getAllActive().first().firstOrNull()?.isDefault }.getOrDefault(null)
        Log.e("LIVE", "RESULT emails(INBOX)=$emailsInbox allEmails=$allEmails events=$events contacts=$contacts accounts=$accounts default=$def")

        // capture live inbox screenshot to prove rows render
        val ui = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = File(dir, "live_inbox.png"); f.delete()
        ui.takeScreenshot(f)
        Thread.sleep(800)
        Log.e("LIVE", "SHOT ${f.exists()}")

        scenario.close()
        Log.e("LIVE", "DONE emailOk=$emailOk calOk=$calOk conOk=$conOk addOk=$addOk emails=$allEmails events=$events contacts=$contacts")
    }
}
