package com.unifiedcomms

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.ConversationType
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.MessagingRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.ChatSyncEngineImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * LIVE end-to-end verification of the Chat-over-mail sync engine against the REAL
 * houseofmanns.com IMAP/SMTP account (Path A — same transport as email).
 *
 * Chat uses IMAP/SMTP, NOT CalDAV/CardDAV (Path B), so it authenticates with the
 * ACCOUNT password — the same credential the email E2E uses. The DAV app-password is
 * NOT applicable here.
 *
 * What this proves:
 *   1. testConnection — IMAP 993 SSL + acceptAllCerts reaches the server.
 *   2. sendChatMessage — builds a MimeMessage with X-Chat-* headers, IMAP APPENDs it
 *      into the Chat folder (SMTP fallback on append failure).
 *   3. syncAccount — opens the Chat folder, normalizes the message, inserts into Room.
 *   4. Room read-back — the message + conversation land in the local DB.
 *
 * Credentials are NEVER hardcoded. Supply at runtime:
 *   -e password "..."   (the ACCOUNT password, same as HouseOfMannsEmailSyncTest)
 *
 * NOTE: the account LOCKS after >2 wrong passwords in 2 minutes. Enter the correct
 * password. Do not run repeatedly with a bad guess.
 *
 * Shell-quoting trap (verified this project): credentials with $ or * passed via
 * `am instrument -e password '...'` are expanded by the DEVICE sh (single quotes do
 * not survive the adb transport) -> truncated password -> auth failure. Quote on the
 * device side: -e password '<account-password>' (the device sh sees single-quoted).
 * Run with the SAME account password the email E2E uses.
 */
class LiveChatE2ETest {

    private val user = "testbox@houseofmanns.com"

    private fun pass(): String =
        InstrumentationRegistry.getArguments().getString("password")
            ?: error("Supply the live test password via instrumentation arg: -e password \"...\"")

    private fun liveAccount(): Account {
        val accPass = pass()
        return Account(
            id = "live-chat-${UUID.randomUUID().toString().take(8)}",
            name = "Live Chat Test",
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
            // ponytail: chat authenticates over IMAP/SMTP (Path A) with the account
            // password, exactly like the email engine. NOT the DAV app-password.
            authConfig = AuthConfig.AppPassword(user, accPass),
            syncConfig = SyncConfig.Defaults().copy(syncChat = true, chatFolder = "Chat"),
            uiConfig = UIConfig.Defaults()
        )
    }

    @Test
    fun liveChatRoundTrip(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val messagingRepo = MessagingRepositoryImpl(db.messageDao(), db.conversationDao())
        val engine = ChatSyncEngineImpl(messagingRepo, accountRepo, crypto, this, app)

        val account = liveAccount()
        accountRepo.insert(account)
        val stored = accountRepo.getById(account.id) ?: account

        // 1) Connection (IMAP 993 + acceptAllCerts)
        val conn = engine.testConnection(stored)
        assertTrue("Chat testConnection failed: ${conn.errorMessage}", conn.success)

        // 2) Build a self-directed conversation + a chat message.
        // getCurrentUserId() defaults to "current_user"; include it as a participant so
        // the engine resolves the peer (testbox@...) via getOtherParticipantIds.
        val convId = "chat/$user"
        val conversation = Conversation(
            id = convId,
            participantIds = listOf("current_user", user),
            participantNames = mapOf("current_user" to "Me", user to "Testbox"),
            type = ConversationType.DIRECT
        )
        val msgId = UUID.randomUUID().toString()
        val message = Message(
            id = msgId,
            conversationId = convId,
            senderId = "current_user",
            recipientId = user,
            content = "Live chat E2E from UnifiedComms at ${System.currentTimeMillis()}"
        )

        // 3) Send (IMAP APPEND into the Chat folder, SMTP fallback)
        val sent = engine.sendChatMessage(stored, conversation, message)
        assertTrue("sendChatMessage failed: ${sent.errorMessage}", sent.success)

        // 4) Sync the chat folder (normalize + insert into Room)
        val sync = engine.syncAccount(stored)
        assertTrue("chat syncAccount failed: ${sync.errorMessage}", sync.success)

        // 5) Read back from Room — the message must be persisted.
        val persisted = messagingRepo.getMessageById(msgId)
        assertTrue("sent chat message not found in Room after sync (sync=$sync)", persisted != null)
        assertTrue(
            "persisted chat message content mismatch: '${persisted?.content}'",
            persisted?.content?.contains("Live chat E2E") == true
        )

        accountRepo.delete(account.id)
    }
}
