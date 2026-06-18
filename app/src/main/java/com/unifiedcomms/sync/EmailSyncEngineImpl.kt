package com.unifiedcomms.sync

import android.content.Context
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.EmailAddress
import com.unifiedcomms.data.model.EmailFlags
import com.unifiedcomms.data.model.EmailRecipients
import com.unifiedcomms.data.model.SystemLabels
import com.unifiedcomms.data.repository.EmailRepository
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Properties
import javax.mail.Session
import javax.mail.Store
import javax.mail.Folder
import javax.mail.Message as JMailMessage
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.RecipientType
import javax.mail.Part
import javax.mail.Multipart
import javax.mail.MimeMultipart
import javax.mail.internet.MimeMessage
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import kotlinx.datetime.Clock

class EmailSyncEngineImpl(
    private val emailRepo: EmailRepository,
    private val accountRepo: AccountRepository,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope
) : EmailSyncEngine {

    private val _syncProgress = MutableStateFlow<Map<String, SyncProgress>>(emptyMap())
    override val syncProgress: StateFlow<Map<String, SyncProgress>> = _syncProgress

    override suspend fun syncAccount(account: Account): SyncResult {
        return syncFolders(account, account.syncConfig.foldersToSync)
    }

    override suspend fun syncFolder(account: Account, folder: String): SyncResult {
        return syncFolders(account, listOf(folder))
    }

    private suspend fun syncFolders(account: Account, folders: List<String>): SyncResult {
        val config = account.serverConfig
        val auth = crypto.decryptAuthConfig(account.authConfig)

        return withContext(Dispatchers.IO) {
            try {
                updateProgress(account.id, folder = null, SyncStage.CONNECTING, 0, 0)

                val props = Properties().apply {
                    put("mail.imap.host", config.imapHost)
                    put("mail.imap.port", config.imapPort)
                    put("mail.imap.ssl.enable", config.imapUseSsl)
                    put("mail.imap.auth", true)
                    put("mail.imap.connectiontimeout", 30000)
                    put("mail.imap.timeout", 30000)
                }

                val session = Session.getInstance(props)
                val store = session.getStore("imap")

                updateProgress(account.id, folder = null, SyncStage.AUTHENTICATING, 0, 0)

                store.connect(config.imapHost, auth.username!!, auth.passwordEncrypted!!)

                var totalSynced = 0
                var totalFailed = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()
                val deletedItems = mutableListOf<String>()

                for (folderName in folders) {
                    updateProgress(account.id, folderName, SyncStage.LISTING_FOLDERS, 0, 0)

                    val folder = store.getFolder(folderName)
                    if (!folder.exists()) continue

                    folder.open(Folder.READ_WRITE)

                    val messageCount = folder.messageCount
                    if (messageCount == 0) {
                        folder.close(false)
                        continue
                    }

                    updateProgress(account.id, folderName, SyncStage.FETCHING_HEADERS, 0, messageCount)

                    // Fetch messages in batches
                    val batchSize = 50
                    for (start in 1..messageCount step batchSize) {
                        val end = minOf(start + batchSize - 1, messageCount)
                        val messages = folder.getMessages(start, end)

                        val fp = FetchProfile()
                        fp.add(FetchProfile.Item.ENVELOPE)
                        fp.add(FetchProfile.Item.FLAGS)
                        fp.add("X-GM-LABELS")
                        folder.fetch(messages, fp)

                        for (msg in messages) {
                            try {
                                val email = parseEmail(msg, account.id, folderName)
                                if (email != null) {
                                    val existing = emailRepo.getByUid(account.id, email.uid, folderName)
                                    if (existing == null) {
                                        emailRepo.insert(email)
                                        newItems.add(email.id)
                                    } else if (existing.etag != email.etag || existing.flags != email.flags) {
                                        emailRepo.update(existing.copy(
                                            flags = email.flags,
                                            labels = email.labels,
                                            systemLabels = email.systemLabels,
                                            etag = email.etag,
                                            updatedAt = Clock.System.now(),
                                            needsSync = false
                                        ))
                                        updatedItems.add(existing.id)
                                    }
                                    totalSynced++
                                }
                            } catch (e: Exception) {
                                totalFailed++
                            }
                        }

                        updateProgress(account.id, folderName, SyncStage.FETCHING_HEADERS, end, messageCount)
                    }

                    folder.close(false)
                }

                store.close()

                updateProgress(account.id, folder = null, SyncStage.COMPLETED, totalSynced, totalSynced)

                return@withContext SyncResult.success(
                    itemsSynced = totalSynced,
                    newItems = newItems,
                    updatedItems = updatedItems,
                    deletedItems = deletedItems
                )

            } catch (e: Exception) {
                updateProgress(account.id, folder = null, SyncStage.ERROR, 0, 0)
                return@withContext SyncResult.failure(e.message ?: "Unknown error", totalFailed)
            }
        }
    }

    private fun parseEmail(msg: JMailMessage, accountId: String, folder: String): Email? {
        try {
            val messageId = msg.messageID ?: return null
            val uid = msg.getHeader("X-GM-LABELS").firstOrNull()?.let { msg.messageNumber.toString() } ?: msg.messageNumber.toString()
            val threadId = msg.getHeader("X-GM-THRID").firstOrNull() ?: messageId
            val inReplyTo = msg.getHeader("In-Reply-To").firstOrNull()
            val references = msg.getHeader("References").toList()

            val sender = EmailAddress(
                name = msg.getHeader("From").firstOrNull()?.takeIf { it.contains("<") }?.substringBefore("<")?.trim(),
                email = msg.getHeader("From").firstOrNull()?.takeIf { it.contains("<") }?.substringAfter("<")?.substringBefore(">")?.trim()
                    ?: msg.getHeader("From").firstOrNull()?.trim() ?: ""
            )

            val recipients = EmailRecipients(
                to = parseAddresses(msg, RecipientType.TO),
                cc = parseAddresses(msg, RecipientType.CC),
                bcc = parseAddresses(msg, RecipientType.BCC),
                replyTo = parseAddresses(msg, RecipientType.REPLY_TO)
            )

            val subject = msg.subject ?: ""
            val sentAt = msg.sentDate?.time ?: System.currentTimeMillis()
            val receivedAt = msg.receivedDate?.time ?: System.currentTimeMillis()

            var bodyText: String? = null
            var bodyHtml: String? = null
            val attachments = mutableListOf<com.unifiedcomms.data.model.Attachment>()

            extractContent(msg, bodyText, bodyHtml, attachments)

            val preview = bodyText?.take(200) ?: subject
            val flags = EmailFlags(
                isRead = msg.isSet(Flags.Flag.SEEN),
                isFlagged = msg.isSet(Flags.Flag.FLAGGED),
                isAnswered = msg.isSet(Flags.Flag.ANSWERED),
                isForwarded = subject.startsWith("Fwd:") || subject.startsWith("Forwarded:")
            )

            val systemLabels = SystemLabels(
                inbox = folder.equals("INBOX", ignoreCase = true) || folder.equals("Inbox", ignoreCase = true),
                sent = folder.equals("Sent", ignoreCase = true),
                draft = folder.equals("Drafts", ignoreCase = true) || folder.equals("Draft", ignoreCase = true),
                trash = folder.equals("Trash", ignoreCase = true),
                spam = folder.equals("Spam", ignoreCase = true) || folder.equals("Junk", ignoreCase = true)
            )

            return Email(
                accountId = accountId,
                folder = folder,
                uid = uid,
                messageId = messageId,
                threadId = threadId,
                inReplyTo = inReplyTo,
                references = references,
                sender = sender,
                recipients = recipients,
                subject = subject,
                bodyText = bodyText,
                bodyHtml = bodyHtml,
                preview = preview,
                sentAt = kotlinx.datetime.Instant.fromEpochMilliseconds(sentAt),
                receivedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(receivedAt),
                flags = flags,
                labels = systemLabels.categorized.keys.toList(),
                systemLabels = systemLabels,
                attachments = attachments,
                sizeBytes = msg.size.toLong(),
                mimeType = msg.contentType
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseAddresses(msg: JMailMessage, type: RecipientType): List<EmailAddress> {
        return try {
            msg.getRecipients(type)?.map { addr ->
                EmailAddress(
                    name = addr.toString().takeIf { it.contains("<") }?.substringBefore("<")?.trim(),
                    email = addr.toString().takeIf { it.contains("<") }?.substringAfter("<")?.substringBefore(">")?.trim()
                        ?: addr.toString().trim()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractContent(part: Part, bodyText: String?, bodyHtml: String?, attachments: MutableList<com.unifiedcomms.data.model.Attachment>) {
        try {
            when {
                part.isMimeType("text/plain") -> {
                    bodyText = part.content as String?
                }
                part.isMimeType("text/html") -> {
                    bodyHtml = part.content as String?
                }
                part.isMimeType("multipart/*") -> {
                    val mp = part.content as MimeMultipart
                    for (i in 0 until mp.count) {
                        extractContent(mp.getBodyPart(i), bodyText, bodyHtml, attachments)
                    }
                }
                Part.ATTACHMENT == part.disposition || part.fileName != null -> {
                    val attachment = com.unifiedcomms.data.model.Attachment(
                        fileName = part.fileName ?: "attachment",
                        mimeType = part.contentType,
                        sizeBytes = part.size.toLong(),
                        contentId = part.contentId,
                        isInline = Part.INLINE == part.disposition
                    )
                    attachments.add(attachment)
                }
            }
        } catch (e: Exception) {
            // Ignore extraction errors
        }
    }

    override suspend fun fetchMessage(account: Account, folder: String, uid: String): Email? {
        // Implementation for fetching a single message
        return null
    }

    override suspend fun sendEmail(account: Account, email: Email): SendResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)

                val props = Properties().apply {
                    put("mail.smtp.host", config.smtpHost)
                    put("mail.smtp.port", config.smtpPort)
                    put("mail.smtp.auth", true)
                    put("mail.smtp.starttls.enable", config.smtpUseStartTls)
                    put("mail.smtp.connectiontimeout", 30000)
                    put("mail.smtp.timeout", 30000)
                }

                val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication() = javax.mail.PasswordAuthentication(auth.username!!, auth.passwordEncrypted!!)
                })

                val mimeMessage = MimeMessage(session)
                mimeMessage.setFrom(InternetAddress(email.sender.email, email.sender.name))

                email.recipients.to.forEach { mimeMessage.addRecipient(RecipientType.TO, InternetAddress(it.email, it.name)) }
                email.recipients.cc.forEach { mimeMessage.addRecipient(RecipientType.CC, InternetAddress(it.email, it.name)) }
                email.recipients.bcc.forEach { mimeMessage.addRecipient(RecipientType.BCC, InternetAddress(it.email, it.name)) }
                email.recipients.replyTo.forEach { mimeMessage.addRecipient(RecipientType.REPLY_TO, InternetAddress(it.email, it.name)) }

                mimeMessage.subject = email.subject

                if (email.bodyHtml != null) {
                    mimeMessage.setContent(email.bodyHtml, "text/html; charset=utf-8")
                } else {
                    mimeMessage.setText(email.bodyText ?: "", "utf-8")
                }

                Transport.send(mimeMessage)
                SendResult.success(mimeMessage.messageID ?: java.util.UUID.randomUUID().toString())
            } catch (e: Exception) {
                SendResult.failure(e.message ?: "Send failed")
            }
        }
    }

    override suspend fun moveToFolder(account: Account, uids: List<String>, fromFolder: String, toFolder: String): SyncResult {
        // IMAP MOVE implementation
        return SyncResult.success()
    }

    override suspend fun deleteMessages(account: Account, folder: String, uids: List<String>): SyncResult {
        // IMAP DELETE implementation
        return SyncResult.success()
    }

    override fun observeSyncProgress(accountId: String): kotlinx.coroutines.flow.Flow<SyncProgress> {
        return _syncProgress.map { it[accountId] ?: SyncProgress(accountId, null, SyncStage.COMPLETED, 0, 0) }.distinctUntilChanged()
    }

    override suspend fun testConnection(account: Account): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)

                val props = Properties().apply {
                    put("mail.imap.host", config.imapHost)
                    put("mail.imap.port", config.imapPort)
                    put("mail.imap.ssl.enable", config.imapUseSsl)
                    put("mail.imap.connectiontimeout", 10000)
                    put("mail.imap.timeout", 10000)
                }
                val session = Session.getInstance(props)
                val store = session.getStore("imap")
                store.connect(config.imapHost, auth.username!!, auth.passwordEncrypted!!)
                store.close()
                ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("IMAP"))
            } catch (e: Exception) {
                ConnectionTestResult(false, 0, emptyList(), e.message)
            }
        }
    }

    private fun updateProgress(accountId: String, folder: String?, stage: SyncStage, current: Int, total: Int) {
        _syncProgress.value = _syncProgress.value + (accountId to SyncProgress(accountId, folder, stage, current, total))
    }
}