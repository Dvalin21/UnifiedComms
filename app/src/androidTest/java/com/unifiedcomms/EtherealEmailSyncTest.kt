package com.unifiedcomms

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.EmailAddress
import com.unifiedcomms.data.model.EmailRecipients
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * E2E verification of the email sync engine (Phase 1/2 fixes) against a live
 * Ethereal IMAP/SMTP test account (ethereal.email). Exercises the exact code path
 * the app uses: testConnection -> sendEmail -> syncAccount -> Room read-back.
 */
class EtherealEmailSyncTest {

    private val user = "rjspzbuvgo66fj6u@ethereal.email"
    private val pass = "Km86CVbtX9Gj6py59Q"

    @Test
    fun fullEmailSyncRoundTrip(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as com.unifiedcomms.UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val engine = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, this)

        val account = Account(
            id = "ethereal-test",
            name = "Ethereal Test",
            email = user,
            accountType = AccountType.GENERIC_IMAP_SMTP,
            serverConfig = ServerConfig(
                imapHost = "imap.ethereal.email",
                imapPort = 993,
                imapUseSsl = true,
                smtpHost = "smtp.ethereal.email",
                smtpPort = 587,
                smtpUseStartTls = true
            ),
            authConfig = AuthConfig.AppPassword(user, pass),
            syncConfig = SyncConfig.Defaults(),
            uiConfig = UIConfig.Defaults()
        )
        accountRepo.insert(account)

        // Re-fetch the persisted (encrypted) account — mirrors SyncManager.performFullSync,
        // which must use the DB record (ciphertext) rather than the in-memory plaintext object.
        val stored = accountRepo.getById(account.id) ?: account

        // 1) Connection test (proves IMAP SSL + SMTP STARTTLS reach the server)
        val conn = engine.testConnection(stored)
        assertTrue("testConnection failed: ${conn.errorMessage}", conn.success)

        // 2) Send a self-mail (proves Phase 1 send actually transmits over SMTP)
        val sent = engine.sendEmail(
            stored,
            Email(
                id = UUID.randomUUID().toString(),
                accountId = account.id,
                folder = "SENT",
                uid = "0",
                messageId = "<uc-test-${System.currentTimeMillis()}@ethereal.email>",
                threadId = "uc-test",
                sender = EmailAddress(null, user),
                recipients = EmailRecipients(to = listOf(EmailAddress(null, user))),
                subject = "UnifiedComms E2E verification",
                bodyText = "Sent from the instrumented sync-engine test.",
                sentAt = kotlinx.datetime.Clock.System.now()
            )
        )
        assertTrue("sendEmail failed: ${sent.errorMessage}", sent.success)

        // 3) Sync (proves Phase 2 IMAP fetch + Converters + Room insert)
        val sync = engine.syncAccount(stored)
        assertTrue("syncAccount failed: ${sync.errorMessage}", sync.success)

        // 4) Read back from Room (proves the full pipeline persisted real data)
        val total = emailRepo.getTotalCount(account.id)
        val inboxCount = emailRepo.getCount(account.id, "INBOX")
        val sentCount = emailRepo.getCount(account.id, "SENT")
        assertTrue(
            "Expected persisted emails. sync=$sync total=$total inbox=$inboxCount sent=$sentCount",
            total > 0
        )
    }
}
