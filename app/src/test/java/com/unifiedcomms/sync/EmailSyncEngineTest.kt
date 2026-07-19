package com.unifiedcomms.sync

import com.unifiedcomms.data.db.dao.EmailDao
import com.unifiedcomms.data.model.*
import com.unifiedcomms.data.repository.EmailRepository
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EmailSyncEngineTest {

    private lateinit var emailRepo: EmailRepository
    private lateinit var accountRepo: AccountRepository
    private lateinit var crypto: CryptoManager
    private lateinit var engine: EmailSyncEngineImpl

    @Before
    fun setUp() {
        val emailDao: EmailDao = mock()
        emailRepo = EmailRepositoryImpl(emailDao)

        accountRepo = mock()
        whenever(accountRepo.getAllActive()).thenReturn(flowOf(emptyList()))

        crypto = mock()
        whenever(crypto.decryptAuthConfig(any())).thenAnswer { it.arguments[0] }

        engine = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, TestScope())
    }

    @Test
    fun `syncAccount fails cleanly with unconfigured account`() = runTest {
        // createGoogle() has no real IMAP server config, so openImapSession throws and the
        // engine must return a failure result (not crash, not a fake-green success).
        val account = Account.createGoogle("user@example.com")
        val result = engine.syncAccount(account)
        println("syncAccount success=${result.success} error=${result.errorMessage}")
        assertFalse("syncAccount must report failure for an unconfigured account", result.success)
        assertNotNull("failure result must carry an error message", result.errorMessage)
    }

    @Test
    fun `sendEmail fails when no SMTP transport is configured`() = runTest {
        val account = Account.createGoogle("user@example.com")
        val email = Email(
            accountId = account.id,
            folder = "Sent",
            uid = "uid-send-1",
            messageId = "<msg1>",
            threadId = "<msg1>",
            sender = EmailAddress("Me", "me@example.com"),
            recipients = EmailRecipients(to = listOf(EmailAddress("You", "you@example.com"))),
            subject = "Test",
            bodyText = "Hello",
            sentAt = kotlinx.datetime.Clock.System.now(),
            receivedAt = kotlinx.datetime.Clock.System.now(),
            flags = EmailFlags(),
            labels = emptyList(),
            systemLabels = SystemLabels(),
            attachments = emptyList(),
            sizeBytes = 0,
            mimeType = "text/plain"
        )
        val result = engine.sendEmail(account, email)
        println("sendEmail success=${result.success} messageId=${result.messageId} error=${result.errorMessage}")
        assertFalse("sendEmail must report failure without a usable SMTP transport", result.success)
        assertNotNull("failure result must carry an error message", result.errorMessage)
    }
}
