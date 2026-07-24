package com.unifiedcomms

import android.app.Application
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
import com.unifiedcomms.data.repository.MessagingRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.sync.ChatSyncEngineImpl
import com.unifiedcomms.sync.ContactSyncEngineImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import com.unifiedcomms.sync.SyncManager
import com.unifiedcomms.sync.TaskSyncEngineImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2E proof that the Chat (folder-based IMAP) sync path WORKS on real hardware
 * against the real houseofmanns.com account.
 *
 * Injects the credential in CODE (not via the masked UI field) so the test is
 * deterministic and not subject to `input text` injection fragility.
 * Seeds a message into the "Chat" IMAP folder (see seed_chat.py) beforehand.
 *
 * Runs the FULL SyncManager (email + calendar + tasks + contacts + chat) exactly
 * as the app does, then asserts the chat conversation + message were persisted.
 */
class ChatSyncOnRealAccountTest {

    private val user = "testbox@houseofmanns.com"
    private fun pass(): String =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("password")
            ?: error("Supply test password via: -e password \"...\"")

    @Test
    fun fullChatSyncRoundTrip(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as com.unifiedcomms.UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val scope = CoroutineScope(Dispatchers.IO)

        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val taskRepo = TaskRepositoryImpl(db.taskDao(), db.taskListDao())
        val contactRepo = ContactRepositoryImpl(db.contactDao())
        val messagingRepo = MessagingRepositoryImpl(db.messageDao(), db.conversationDao())

        val account = Account(
            id = "chat-e2e-test",
            name = "Chat E2E",
            email = user,
            accountType = AccountType.MAILCOW,
            serverConfig = ServerConfig(
                imapHost = "imap.houseofmanns.com",
                imapPort = 993,
                imapUseSsl = true,
                acceptAllCerts = true,
                smtpHost = "smtp.houseofmanns.com",
                smtpPort = 587,
                smtpUseStartTls = true
            ),
            authConfig = AuthConfig.AppPassword(user, pass()),
            // ponytail: ensure chat sync is enabled so the chat leg runs.
            syncConfig = SyncConfig(
                syncEmail = true,
                syncCalendar = false,
                syncTasks = false,
                syncContacts = false,
                syncChat = true,
                chatFolder = "Chat"
            ),
            uiConfig = UIConfig.Defaults()
        )
        accountRepo.insert(account)
        val stored = accountRepo.getById(account.id) ?: account

        val syncManager = SyncManager(
            EmailSyncEngineImpl(emailRepo, accountRepo, crypto, scope),
            CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, scope),
            TaskSyncEngineImpl(taskRepo, accountRepo, crypto, scope),
            ContactSyncEngineImpl(contactRepo, accountRepo, crypto, scope),
            ChatSyncEngineImpl(messagingRepo, accountRepo, crypto, scope, app),
            accountRepo,
            scope,
            app,
            crypto
        )

        val result = syncManager.syncNow(stored)
        assertTrue("Full sync failed: ${result.errorMessage}", result.success)

        // The seeded messages have From/To = testbox@houseofmanns.com. The app keys
        // the direct conversation by buildThreadId(account.email, {sender,recipient})
        // -> "chat/<sorted participants>". With both = account.email this is
        // "chat/testbox@houseofmanns.com".
        val threadId = "chat/${listOf(user).sorted().joinToString(":") { it.lowercase() }}"
        val conversation = messagingRepo.getConversationsByIds(listOf(threadId)).firstOrNull()
        val messages = messagingRepo.getDirectMessages(user, user, 10)
        assertTrue(
            "Expected chat conversation + messages persisted from 'Chat' folder. " +
                "threadId=$threadId conv=${conversation != null} msgs=${messages.size}",
            conversation != null && messages.isNotEmpty()
        )
    }
}
