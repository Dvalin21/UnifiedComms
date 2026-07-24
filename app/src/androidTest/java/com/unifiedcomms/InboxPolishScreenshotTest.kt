package com.unifiedcomms

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Polished-inbox visual proof: seeds a REAL active account in code (correct
 * creds, no flaky UI text entry), syncs INBOX, launches the actual MainActivity,
 * and captures a screenshot of the rendered inbox to /sdcard for human review.
 */
class InboxPolishScreenshotTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun password(): String =
        InstrumentationRegistry.getArguments().getString("password")
            ?: error("Supply live password via: -e password '...'")

    private val user = "testbox@houseofmanns.com"

    @Test
    fun capturePolishedInbox(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = EmailRepositoryImpl(db.emailDao())

        com.unifiedcomms.util.PreferencesManager.initialize(app)
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", false)

        val acc = Account(
            id = "polish-${UUID.randomUUID().toString().take(8)}",
            name = "Rend ($user)",
            email = user,
            accountType = AccountType.MAILCOW,
            serverConfig = ServerConfig(
                imapHost = "imap.houseofmanns.com", imapPort = 993, imapUseSsl = true, acceptAllCerts = true,
                smtpHost = "smtp.houseofmanns.com", smtpPort = 587, smtpUseStartTls = true
            ),
            authConfig = AuthConfig.AppPassword(user, password()),
            syncConfig = SyncConfig.Defaults().copy(syncEmail = true, syncCalendar = false, syncTasks = false, syncContacts = false),
            uiConfig = UIConfig.Defaults()
        )
        accountRepo.insert(acc)
        val stored = accountRepo.getById(acc.id) ?: acc
        EmailSyncEngineImpl(emailRepo, accountRepo, crypto, this).syncAccount(stored)

        val count = emailRepo.getTotalCount(acc.id)
        android.util.Log.e("INBOXPOLISH", "synced emails=$count")

        composeTestRule.waitForIdle()
        Thread.sleep(3500)

        val dir = File("/sdcard/Download")
        val shot = File(dir, "inbox_polished.png")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(shot)
        android.util.Log.e("INBOXPOLISH", "screenshot saved: ${shot.absolutePath} exists=${shot.exists()} size=${shot.length()}")

        accountRepo.delete(acc.id)
    }
}
