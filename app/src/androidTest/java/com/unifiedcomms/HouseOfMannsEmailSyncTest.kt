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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * E2E verification against the REAL houseofmanns.com IMAP/SMTP account.
 * Config: IMAP 993 SSL + acceptAllCerts (server cert is *.houseofmanns.com,
 * which does NOT match bare houseofmanns.com, so strict verification fails;
 * SMTP 587 STARTTLS. Exercises the exact app code path:
 * testConnection -> sendEmail -> syncAccount -> Room read-back.
 */
class HouseOfMannsEmailSyncTest {

    private val user = "testbox@houseofmanns.com"
    // ponytail: NEVER hardcode credentials in source. The password is injected at
    // test runtime via instrumentation arg: -e password "...". Read it inside the
    // test (not at class-init) using the correct androidx.test API.
    private fun pass(): String =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("password")
            ?: error("Supply test password via: -e password \"...\"")

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
            id = "houseofmanns-test",
            name = "HouseOfManns Test",
            email = user,
            accountType = AccountType.GENERIC_IMAP_SMTP,
            serverConfig = ServerConfig(
                imapHost = "houseofmanns.com",
                imapPort = 993,
                imapUseSsl = true,
                acceptAllCerts = true,
                smtpHost = "houseofmanns.com",
                smtpPort = 587,
                smtpUseStartTls = true
            ),
            authConfig = AuthConfig.AppPassword(user, pass()),
            syncConfig = SyncConfig.Defaults(),
            uiConfig = UIConfig.Defaults()
        )
        accountRepo.insert(account)

        val stored = accountRepo.getById(account.id) ?: account

        // 1) Connection test (proves 993 + acceptAllCerts reaches the server)
        val conn = engine.testConnection(stored)
        assertTrue("testConnection failed: ${conn.errorMessage}", conn.success)

        // 2) Send a self-mail (proves SMTP 587 STARTTLS transmits)
        val sent = engine.sendEmail(
            stored,
            Email(
                id = UUID.randomUUID().toString(),
                accountId = account.id,
                folder = "SENT",
                uid = "0",
                messageId = "<uc-hom-${System.currentTimeMillis()}@houseofmanns.com>",
                threadId = "uc-hom-test",
                sender = EmailAddress(null, user),
                recipients = EmailRecipients(to = listOf(EmailAddress(null, user))),
                subject = "UnifiedComms HouseOfManns E2E",
                bodyText = "Sent from the instrumented sync-engine test.",
                sentAt = kotlinx.datetime.Clock.System.now()
            )
        )
        assertTrue("sendEmail failed: ${sent.errorMessage}", sent.success)

        // 3) Sync (proves IMAP 993 fetch + Converters + Room insert)
        val sync = engine.syncAccount(stored)
        assertTrue("syncAccount failed: ${sync.errorMessage}", sync.success)

        // 4) Read back from Room
        val total = emailRepo.getTotalCount(account.id)
        val inboxCount = emailRepo.getCount(account.id, "INBOX")
        assertTrue(
            "Expected persisted emails. sync=$sync total=$total inbox=$inboxCount",
            total > 0
        )
    }
}
