package com.unifiedcomms.sync

import android.content.Context
import android.util.Log
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.ConversationType
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.getCurrentUserId
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.MessagingRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message as JMailMessage
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class ChatSyncEngineImpl(
    private val messagingRepo: MessagingRepository,
    private val accountRepo: AccountRepository,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope,
    private val context: Context
) : ChatSyncEngine {
    private val _syncProgress = MutableStateFlow<Map<String, SyncProgress>>(emptyMap())
    val syncProgress: StateFlow<Map<String, SyncProgress>> = _syncProgress

    override suspend fun syncAccount(account: Account): SyncResult {
        val stored = accountRepo.getById(account.id) ?: account
        var effectiveFolder = stored.syncConfig.chatFolder
        if (effectiveFolder.isBlank()) {
            effectiveFolder = resolveChatFolder(stored)
            if (effectiveFolder.isNotBlank() && effectiveFolder != stored.syncConfig.chatFolder) {
                // ponytail: accountRepo.update re-encrypts authConfig; `stored` came from
                // getById (already encrypted), so re-encrypting would double-encrypt.
                // Re-encrypt the decrypted config to get a clean plaintext->GCM round-trip.
                val auth = crypto.decryptAuthConfig(stored.authConfig)
                val reEncrypted = stored.copy(authConfig = crypto.encryptAuthConfig(auth))
                accountRepo.update(reEncrypted.copy(syncConfig = reEncrypted.syncConfig.copy(chatFolder = effectiveFolder)))
            }
        }
        return syncFolder(stored, effectiveFolder)
    }

    override suspend fun syncFolder(account: Account, folder: String): SyncResult {
        if (folder.isBlank()) return SyncResult.success(0)
        val config = account.serverConfig
        val auth = crypto.decryptAuthConfig(account.authConfig)
        return withContext(Dispatchers.IO) {
            updateProgress(account.id, folder, SyncStage.CONNECTING, 0, 0)
            try {
                val store = connectChatStore(config, auth)
                updateProgress(account.id, folder, SyncStage.AUTHENTICATING, 0, 0)

                val f = store.getFolder(folder)
                if (!f.exists()) {
                    f.create(Folder.HOLDS_MESSAGES)
                }
                f.open(Folder.READ_WRITE)
                val messageCount = f.messageCount
                if (messageCount == 0) {
                    f.close(false)
                    store.close()
                    return@withContext SyncResult.success(0)
                }
                updateProgress(account.id, folder, SyncStage.FETCHING_HEADERS, 0, messageCount)

                var synced = 0
                val batchSize = 50
                for (start in 1..messageCount step batchSize) {
                    val end = minOf(start + batchSize - 1, messageCount)
                    val messages = f.getMessages(start, end)
                    val fp = javax.mail.FetchProfile()
                    fp.add(javax.mail.FetchProfile.Item.ENVELOPE)
                    fp.add("BODY.PEEK[]")
                    f.fetch(messages, fp)

                    for (msg in messages) {
                        if (!f.isOpen) break
                        val chatUid = msg.getHeader("X-Chat-Message-Id")?.firstOrNull()
                            ?: msg.getHeader("Message-ID")?.firstOrNull()
                            ?: "${folder}#${msg.messageNumber}"
                        val existing = messagingRepo.getMessageById(chatUid)
                        if (existing == null) {
                            val normalized = normalizeChatMessage(msg, account, folder, chatUid)
                            if (normalized != null) {
                                messagingRepo.insertMessage(normalized.first)
                                normalized.second?.let { messagingRepo.updateConversation(it) }
                                synced++
                            }
                        }
                    }
                    updateProgress(account.id, folder, SyncStage.FETCHING_HEADERS, end, messageCount)
                }
                f.close(false)
                store.close()
                updateProgress(account.id, folder, SyncStage.COMPLETED, synced, messageCount)
                SyncResult.success(synced)
            } catch (e: Exception) {
                updateProgress(account.id, folder, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Chat sync failed")
            }
        }
    }

    override suspend fun sendChatMessage(account: Account, conversation: Conversation, message: Message): SendResult {
        val config = account.serverConfig
        val auth = crypto.decryptAuthConfig(account.authConfig)
        return withContext(Dispatchers.IO) {
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", config.smtpHost)
                    put("mail.smtp.port", config.smtpPort)
                    put("mail.smtp.auth", true)
                    put("mail.smtp.starttls.enable", config.smtpUseStartTls)
                    put("mail.smtp.connectiontimeout", 30000)
                    put("mail.smtp.timeout", 30000)
                }

                val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication(): javax.mail.PasswordAuthentication {
                        val (user, pass) = chatCredentials(auth)
                        return javax.mail.PasswordAuthentication(user, pass)
                    }
                })

                val peer = conversation.getOtherParticipantIds(getCurrentUserId()).firstOrNull()
                    ?: conversation.participantIds.firstOrNull() ?: return@withContext SendResult.failure("No recipient")

                val mimeMessage = MimeMessage(session)
                mimeMessage.setFrom(InternetAddress(account.email, account.name))
                mimeMessage.setRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(peer))

                mimeMessage.subject = buildChatSubject(message, conversation)
                mimeMessage.setHeader("X-Chat-Message-Id", message.id)
                mimeMessage.setHeader("X-Chat-Conversation-Id", conversation.id)
                mimeMessage.setHeader("X-Chat-Sender-Id", message.senderId)

                if (conversation.title != null) {
                    mimeMessage.setHeader("X-Chat-Group-Title", conversation.title)
                }
                if (message.replyToId != null) {
                    mimeMessage.setHeader("In-Reply-To", message.replyToId)
                    mimeMessage.setHeader("References", message.replyToId)
                }

                val text = buildChatBody(message, conversation)
                mimeMessage.setText(text, "utf-8")

                val savedFolder = account.syncConfig.chatFolder.ifBlank { FALLBACK_CHAT_FOLDER }

                if (savedFolder.isNotBlank()) {
                    try {
                        val store = connectChatStore(config, auth)
                        val f = store.getFolder(savedFolder)
                        if (!f.exists()) f.create(Folder.HOLDS_MESSAGES)
                        f.open(Folder.READ_WRITE)
                        f.appendMessages(arrayOf(mimeMessage))
                        f.close(false)
                        store.close()
                    } catch (e: Exception) {
                        Log.w("ChatSyncEngineImpl", "IMAP append failed, falling back to SMTP: ${e.message}")
                        javax.mail.Transport.send(mimeMessage)
                    }
                } else {
                    javax.mail.Transport.send(mimeMessage)
                }

                val mid = mimeMessage.getHeader("Message-ID")?.firstOrNull() ?: message.id
                SendResult.success(mid)
            } catch (e: Exception) {
                SendResult.failure(e.message ?: "Chat send failed")
            }
        }
    }

    override suspend fun testConnection(account: Account): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val session = Session.getInstance(openImapProps(config))
                val store = session.getStore("imap")
                val (user, pass) = chatCredentials(auth)
                store.connect(config.imapHost, user, pass)
                store.close()
                ConnectionTestResult(true, System.currentTimeMillis() - System.currentTimeMillis(), listOf("IMAP Chat"), null)
            } catch (e: Exception) {
                ConnectionTestResult(false, 0, emptyList(), e.message)
            }
        }
    }

    override fun observeSyncProgress(accountId: String): kotlinx.coroutines.flow.Flow<SyncProgress> {
        return _syncProgress.transform { progressMap ->
            emit(progressMap[accountId] ?: SyncProgress(accountId, null, SyncStage.COMPLETED, 0, 0))
        }.distinctUntilChanged()
    }

    private fun normalizeChatMessage(msg: JMailMessage, account: Account, folder: String, chatUid: String): Pair<Message, Conversation>? {
        return try {
            val fromRaw = msg.getHeader("From")?.firstOrNull() ?: return null
            val senderEmail = InternetAddress.parse(fromRaw).firstOrNull()?.address ?: return null
            val senderId = senderEmail
            val currentUserId = getCurrentUserId()
            val recipientRaw = msg.getHeader("To")?.firstOrNull() ?: ""
            val recipientEmail = InternetAddress.parse(recipientRaw).firstOrNull()?.address ?: ""

            val subject = msg.getHeader("Subject")?.firstOrNull() ?: ""
            val content = msg.content?.toString() ?: ""
            val chatConvId = msg.getHeader("X-Chat-Conversation-Id")?.firstOrNull()
                ?: buildThreadId(account.email, setOf(senderEmail, recipientEmail))

            val conversation = Conversation(
                id = chatConvId,
                participantIds = listOf(currentUserId, recipientEmail),
                participantNames = mapOf(currentUserId to (account.name ?: account.email), recipientEmail to (senderEmail.substringBefore("@"))),
                type = ConversationType.DIRECT,
                lastMessageId = chatUid,
                lastMessagePreview = content.take(100),
                lastActivityAt = kotlinx.datetime.Instant.fromEpochMilliseconds(msg.sentDate?.time ?: System.currentTimeMillis()),
                unreadCount = 0
            )

            val message = Message(
                id = chatUid,
                conversationId = chatConvId,
                senderId = senderId,
                recipientId = if (senderId == account.email) recipientEmail else account.email,
                content = content,
                sentAt = kotlinx.datetime.Instant.fromEpochMilliseconds(msg.sentDate?.time ?: System.currentTimeMillis()),
                status = if (msg.isSet(Flags.Flag.SEEN)) MessageStatus.READ else MessageStatus.SENT,
                isLocalOnly = false,
                needsSync = false
            )
            message to conversation
        } catch (e: Exception) {
            Log.w("ChatSyncEngineImpl", "normalizeChatMessage failed: ${e.message}")
            null
        }
    }

    private fun buildThreadId(accountEmail: String, participants: Set<String>): String {
        val ids = listOf(accountEmail) + participants.filter { it != accountEmail }
        return "chat/${ids.sorted().joinToString(":") { it.lowercase() }}"
    }

    private fun buildChatSubject(message: Message, conversation: Conversation): String {
        val peer = conversation.getOtherParticipantNames(getCurrentUserId()).firstOrNull()
        return conversation.title ?: peer ?: "Chat"
    }

    private fun buildChatBody(message: Message, conversation: Conversation): String {
        return buildString {
            appendLine("Chat message from ${message.senderId}")
            if (conversation.title != null) appendLine("Group: ${conversation.title}")
            appendLine()
            append(message.content)
        }
    }

    private fun openImapSession(config: com.unifiedcomms.data.model.ServerConfig): Session {
        return Session.getInstance(openImapProps(config))
    }

    private fun openImapProps(config: com.unifiedcomms.data.model.ServerConfig): Properties {
        return Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", config.imapHost)
            put("mail.imap.port", config.imapPort)
            put("mail.imap.ssl.enable", config.imapUseSsl)
            put("mail.imap.auth", true)
            put("mail.imap.connectiontimeout", 60000)
            put("mail.imap.timeout", 300000)
            put("mail.imap.writetimeout", 120000)
            if (config.acceptAllCerts) {
                put("mail.imap.ssl.checkserveridentity", false)
                val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf(object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                }), java.security.SecureRandom())
                put("mail.imap.ssl.socketFactory", ctx.socketFactory)
            }
        }
    }

    // ponytail: OAuth2 accounts use XOAUTH2 (bearer token) as the IMAP/SMTP password,
    // not the stored password field (which is empty for OAuth). Mirrors EmailSyncEngine.
    private fun chatCredentials(auth: com.unifiedcomms.data.model.AuthConfig): Pair<String, String> =
        if (auth.type == com.unifiedcomms.data.model.AuthType.OAUTH2) {
            auth.username!! to EmailSyncEngineImpl.buildXoauth2Static(auth.username!!, auth.oauthAccessToken.orEmpty())
        } else {
            auth.username!! to (auth.passwordEncrypted ?: "")
        }

    private suspend fun connectChatStore(config: com.unifiedcomms.data.model.ServerConfig, auth: com.unifiedcomms.data.model.AuthConfig): Store {
        val session = openImapSession(config)
        val store = session.store
        val (user, pass) = chatCredentials(auth)
        store.connect(config.imapHost, config.imapPort, user, pass)
        return store
    }

    private fun updateProgress(accountId: String, folder: String?, stage: SyncStage, current: Int, total: Int) {
        _syncProgress.value = _syncProgress.value + (accountId to SyncProgress(accountId, folder, stage, current, total))
    }

    private suspend fun resolveChatFolder(account: Account): String {
        val config = account.serverConfig
        val auth = crypto.decryptAuthConfig(account.authConfig)
        return runCatching {
            val session = Session.getInstance(
                Properties().apply {
                    put("mail.store.protocol", "imap")
                    put("mail.imap.host", config.imapHost)
                    put("mail.imap.port", config.imapPort)
                    put("mail.imap.ssl.enable", config.imapUseSsl)
                    put("mail.imap.auth", true)
                    put("mail.imap.connectiontimeout", 10000)
                    put("mail.imap.timeout", 10000)
                    if (config.acceptAllCerts) {
                        put("mail.imap.ssl.checkserveridentity", false)
                        val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
                        ctx.init(null, arrayOf(object : javax.net.ssl.X509TrustManager {
                            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                        }), java.security.SecureRandom())
                        put("mail.imap.ssl.socketFactory", ctx.socketFactory)
                    }
                }
            )
            val store = session.getStore("imap")
            val (user, pass) = chatCredentials(auth)
            store.connect(config.imapHost, config.imapPort, user, pass)
            val candidates = listOf(FALLBACK_CHAT_FOLDER, DEFAULT_CHAT_FOLDER)
            val found = candidates.firstOrNull { name ->
                store.getFolder(name).let { f -> f.exists() && f.type == Folder.HOLDS_MESSAGES }
            }
            store.close()
            found.orEmpty()
        }.getOrDefault("")
    }

    companion object {
        const val DEFAULT_CHAT_FOLDER = "UnifiedCommsChat"
        const val FALLBACK_CHAT_FOLDER = "Chat"
    }
}
