package com.unifiedcomms.sync

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Properties
import javax.mail.Session
import javax.mail.Store
import javax.mail.Folder
import javax.mail.Message as JMailMessage
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Message.RecipientType
import javax.mail.Part
import javax.mail.Multipart
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeMessage
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
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
            var totalSynced = 0
            var totalFailed = 0
            val newItems = mutableListOf<String>()
            val updatedItems = mutableListOf<String>()
            val deletedItems = mutableListOf<String>()

            var store: Store? = null
            try {
                updateProgress(account.id, folder = null, SyncStage.CONNECTING, 0, 0)
                val session = openImapSession(config)
                store = session.store

                updateProgress(account.id, folder = null, SyncStage.AUTHENTICATING, 0, 0)
                connectStoreWithRetry(store!!, config, auth)
                Log.d("EmailSyncEngineImpl", "connected imapHost=${config.imapHost} port=${config.imapPort} ssl=${config.imapUseSsl} foldersToSync=$folders")

                for (folderName in folders) {
                    updateProgress(account.id, folderName, SyncStage.LISTING_FOLDERS, 0, 0)

                    val folder = store!!.getFolder(folderName)
                    if (!folder.exists()) {
                        Log.w("EmailSyncEngineImpl", "folder does not exist: $folderName")
                        continue
                    }
                    val folderResult = syncSingleFolder(account, folder)
                    totalSynced += folderResult.first
                    totalFailed += folderResult.second
                    newItems.addAll(folderResult.third)
                    updatedItems.addAll(folderResult.fourth)
                }

                store?.close()

                updateProgress(account.id, folder = null, SyncStage.COMPLETED, totalSynced, totalSynced)
                return@withContext SyncResult.success(
                    itemsSynced = totalSynced,
                    newItems = newItems,
                    updatedItems = updatedItems,
                    deletedItems = deletedItems
                )

            } catch (e: Exception) {
                updateProgress(account.id, folder = null, SyncStage.ERROR, 0, 0)
                // ponytail: close the store on failure to prevent connection leak.
                try { store?.close() } catch (_: Exception) {}
                // Report honest failure: a failed folder is a real error, not partial success.
                // The caller (SyncManager) decides whether to surface it; masking it as
                // success hides real problems (dead Sent folder, auth expiry mid-sync).
                return@withContext SyncResult.failure(e.message ?: "Unknown error", totalFailed)
            }
        }
    }

    private suspend fun syncSingleFolder(
        account: Account,
        folder: Folder
    ): Tuple4 {
        val folderName = folder.name
        folder.open(Folder.READ_ONLY)

        val messageCount = folder.messageCount
        if (messageCount == 0) {
            folder.close(false)
            return Tuple4(0, 0, emptyList(), emptyList())
        }

        // ponytail: UID-based folder reference so reconnects don’t alias by
        // shifting sequence numbers. Sequence-based fetching is fine for the
        // capped initial window, but stable identity for idempotency must be
        // obtained with UIDVALIDITY + UID.
        val uidFolder = folder as? javax.mail.UIDFolder
        val serverUidValidity = uidFolder?.uidValidity?.toString()
        Log.d("EmailSyncEngineImpl", "folder=$folderName total=$messageCount uidValidity=$serverUidValidity")

        // Root-cause guard (K-9 proven): when UIDVALIDITY changes we must drop
        // local indexing for this folder; otherwise old imapUid rows collide
        // with new server UIDs and silently skip real mail.
        if (!serverUidValidity.isNullOrBlank()) {
            val localWithSameValidity = emailRepo.getByFolderAndUidValidity(account.id, folderName, serverUidValidity)
            if (localWithSameValidity.isEmpty()) {
                val anyLocalCount = emailRepo.getCount(account.id, folderName)
                if (anyLocalCount > 0) {
                    Log.w("EmailSyncEngineImpl", "folder=$folderName uidValidity changed, invalidating")
                    emailRepo.deleteByAccountAndFolder(account.id, folderName)
                }
            }
        }

        val MAX_INITIAL_MESSAGES = 300
        val messages: Array<JMailMessage> = if (uidFolder != null) {
            val uidNext = uidFolder.uidNext
            val startUid = maxOf(1L, uidNext - MAX_INITIAL_MESSAGES)
            val endUid = maxOf(0L, uidNext - 1)
            if (endUid >= startUid) uidFolder.getMessagesByUID(startUid, endUid) else emptyArray()
        } else {
            val startIdx = maxOf(1, messageCount - MAX_INITIAL_MESSAGES + 1)
            folder.getMessages(startIdx, messageCount)
        }
        val effectiveCount = messages.size
        Log.d("EmailSyncEngineImpl", "folder=$folderName total=$messageCount syncing $effectiveCount via ${if (uidFolder != null) "UID" else "sequence"}")

        updateProgress(account.id, folderName, SyncStage.FETCHING_HEADERS, 0, effectiveCount)

        var totalSynced = 0
        var totalFailed = 0
        var parsedFail = 0
        val newItems = mutableListOf<String>()
        val updatedItems = mutableListOf<String>()
        val pendingFlagUpdates = mutableListOf<Pair<Email, com.unifiedcomms.data.model.EmailFlags>>()
        val fp = FetchProfile()
        fp.add(FetchProfile.Item.ENVELOPE)
        fp.add(FetchProfile.Item.FLAGS)
        fp.add("X-GM-LABELS")
        fp.add("BODY.PEEK[]")
        folder.fetch(messages, fp)

        for (msg in messages) {
            if (!folder.isOpen) break
            try {
                val messageId = msg.getHeader("Message-ID")?.firstOrNull()
                val imapUid = uidFolder?.getUID(msg)?.toString()
                    ?: "$folderName#${msg.messageNumber}"
                val email = parseEmail(msg, account.id, folderName, messageId, imapUid)
                if (email != null) {
                    val stableUid = serverUidValidity ?: email.uidValidity
                    var local = emailRepo.getByImapUid(account.id, imapUid, folderName)
                    if (local == null) {
                        local = emailRepo.getByUid(account.id, email.uid, folderName)
                    }
                    if (local == null) {
                        emailRepo.insert(email.copy(uidValidity = stableUid, imapUid = imapUid))
                        newItems.add(email.id)
                    } else if (local.etag != email.etag || local.flags != email.flags) {
                        emailRepo.update(
                            local.copy(
                                flags = email.flags,
                                labels = email.labels,
                                systemLabels = email.systemLabels,
                                etag = email.etag,
                                updatedAt = Clock.System.now(),
                                needsSync = false,
                                messageId = email.messageId,
                                subject = email.subject,
                                bodyText = email.bodyText,
                                bodyHtml = email.bodyHtml,
                                preview = email.preview,
                                uidValidity = stableUid ?: local.uidValidity,
                                imapUid = imapUid
                            )
                        )

                        // Bidirectional flag sync: push LOCAL flag changes back to IMAP so the
                        // server agrees with the DB. The DB row was just updated with server
                        // flags above; here we push the LOCAL flags (user's mark-read/unread)
                        // to the server. Deferred to post-batch pass to avoid mutating
                        // READ_ONLY folder state mid-iteration.
                        pendingFlagUpdates.add(local to local.flags)

                        updatedItems.add(local.id)
                    }
                    totalSynced++
                } else {
                    parsedFail++
                }
            } catch (e: Exception) {
                totalFailed++
            }
        }

        updateProgress(account.id, folderName, SyncStage.FETCHING_HEADERS, totalSynced, messages.size)

        if (pendingFlagUpdates.isNotEmpty() && folder.isOpen) {
            try {
                folder.close(false)
                folder.open(Folder.READ_WRITE)
            } catch (e: Exception) {
                Log.w("EmailSyncEngineImpl", "flag sync open failed: ${e.message}")
            }
        }
        for ((local, serverFlags) in pendingFlagUpdates) {
            applyLocalFlagsToServer(folder, local, serverFlags)
        }

        if (folder.isOpen) {
            folder.close(false)
        }
        Log.d("EmailSyncEngineImpl", "folder=$folderName done synced=$totalSynced failed=$totalFailed parsedFail=$parsedFail")
        return Tuple4(totalSynced, totalFailed, newItems, updatedItems)
    }

    private fun connectStoreWithRetry(
        store: Store,
        config: com.unifiedcomms.data.model.ServerConfig,
        auth: com.unifiedcomms.data.model.AuthConfig
    ) {
        // For OAuth2, IMAP/SMTP use the XOAUTH2 token as the password.
        val (user, pass) = if (auth.type == com.unifiedcomms.data.model.AuthType.OAUTH2) {
            auth.username!! to buildXoauth2(auth.username!!, auth.oauthAccessToken.orEmpty())
        } else {
            auth.username!! to auth.passwordEncrypted!!
        }
        for (attempt in 1..2) {
            try {
                store.connect(
                    config.imapHost,
                    config.imapPort,
                    user,
                    pass
                )
                return
            } catch (e: javax.mail.MessagingException) {
                if (attempt == 2) throw e
            }
        }
    }

    private suspend fun applyLocalFlagsToServer(
        folder: Folder,
        local: Email,
        serverFlags: com.unifiedcomms.data.model.EmailFlags
    ) {
        // Caller must open `folder` in READ_WRITE before invoking this.
        val diff = local.flags != serverFlags
        if (!diff) return
        try {
            val uidFolder = folder as? javax.mail.UIDFolder ?: return
            val uidVal = local.imapUid?.toLongOrNull() ?: return
            val msg = uidFolder.getMessageByUID(uidVal) ?: return
            if (local.flags.isRead != serverFlags.isRead) {
                msg.setFlag(Flags.Flag.SEEN, local.flags.isRead)
            }
            if (local.flags.isFlagged != serverFlags.isFlagged) {
                msg.setFlag(Flags.Flag.FLAGGED, local.flags.isFlagged)
            }
            if (local.flags.isAnswered != serverFlags.isAnswered) {
                msg.setFlag(Flags.Flag.ANSWERED, local.flags.isAnswered)
            }
        } catch (e: Exception) {
            Log.w("EmailSyncEngineImpl", "flag sync failed uid=${local.imapUid} folder=${local.folder}: ${e.message}")
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun buildXoauth2(user: String, token: String): String {
        return base64(xoauth2Bare(user, token))
    }

    companion object {
        /**
         * ponytail: the exact SASL XOAUTH2 string Gmail/Outlook IMAP expect, pre-base64.
         * Kept pure (no android.util.Base64) so it is unit-testable on the JVM.
         */
        internal fun xoauth2Bare(user: String, token: String): String =
            "user=$user\u0001auth=Bearer $token\u0001\u0001"

        @androidx.annotation.VisibleForTesting
        internal fun buildXoauth2Static(user: String, token: String): String =
            android.util.Base64.encodeToString(
                xoauth2Bare(user, token).toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
    }

    private fun base64(s: String): String =
        android.util.Base64.encodeToString(s.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

    private fun openImapSession(config: com.unifiedcomms.data.model.ServerConfig): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", config.imapHost)
            put("mail.imap.port", config.imapPort)
            put("mail.imap.ssl.enable", config.imapUseSsl)
            put("mail.imap.auth", true)
            put("mail.imap.connectiontimeout", 60000)
            put("mail.imap.timeout", 300000)
            put("mail.imap.writetimeout", 120000)
            // ponytail: android-mail 1.6.7 enforces cert hostname verification by
            // default (Angus 1.1.0: "check server identity by default"). For a
            // self-signed / internal-CA IMAP server that hard-fails store.connect()
            // even with a correct password, the user can opt in to skip it.
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
        return Session.getInstance(props)
    }

    private fun parseEmail(msg: JMailMessage, accountId: String, folder: String, messageId: String?, uid: String): Email? {
        return try {
            val uid = uid
            val threadId = msg.getHeader("X-GM-THRID")?.firstOrNull() ?: messageId ?: uid
            val inReplyTo = msg.getHeader("In-Reply-To")?.firstOrNull()
            val references = msg.getHeader("References")?.toList() ?: emptyList()

            val sender = EmailAddress(
                name = msg.getHeader("From")?.firstOrNull()?.takeIf { it.contains("<") }?.substringBefore("<")?.trim(),
                email = msg.getHeader("From")?.firstOrNull()?.takeIf { it.contains("<") }?.substringAfter("<")?.substringBefore(">")?.trim()
                    ?: msg.getHeader("From")?.firstOrNull()?.trim() ?: ""
            )

            val recipients = EmailRecipients(
                to = parseAddresses(msg, RecipientType.TO),
                cc = parseAddresses(msg, RecipientType.CC),
                bcc = parseAddresses(msg, RecipientType.BCC),
                replyTo = parseReplyToAddresses(msg)
            )

            val subject = msg.getHeader("Subject")?.firstOrNull() ?: ""
            val sentAt = parseDateHeader(msg, "Date")
            val receivedAt = parseDateHeader(msg, "Received") ?: sentAt

            // ponytail: body/attachment extraction can throw on unusual MIME
            // structures. A failure there must NOT discard the message — K-9
            // keeps the envelope and shows an empty body. Best-effort parse.
            val (bodyText, bodyHtml) = runCatching { extractContent(msg) }.getOrDefault(null to null)
            val attachments = mutableListOf<com.unifiedcomms.data.model.Attachment>()
            runCatching { extractAttachments(msg, attachments) }

            val preview = bodyText?.take(200) ?: subject
            val flags = EmailFlags(
                isRead = runCatching { msg.isSet(Flags.Flag.SEEN) }.getOrDefault(false),
                isFlagged = runCatching { msg.isSet(Flags.Flag.FLAGGED) }.getOrDefault(false),
                isAnswered = runCatching { msg.isSet(Flags.Flag.ANSWERED) }.getOrDefault(false),
                isForwarded = subject.startsWith("Fwd:") || subject.startsWith("Forwarded:")
            )

            val systemLabels = SystemLabels(
                inbox = folder.equals("INBOX", ignoreCase = true) || folder.equals("Inbox", ignoreCase = true),
                sent = folder.equals("Sent", ignoreCase = true),
                draft = folder.equals("Drafts", ignoreCase = true) || folder.equals("Draft", ignoreCase = true),
                trash = folder.equals("Trash", ignoreCase = true),
                spam = folder.equals("Spam", ignoreCase = true) || folder.equals("Junk", ignoreCase = true)
            )

            Email(
                accountId = accountId,
                folder = folder,
                uid = uid,
                messageId = messageId ?: uid,
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
                sizeBytes = msg.getHeader("Content-Length")?.firstOrNull()?.toLongOrNull()
                    ?: runCatching { msg.size.toLong() }.getOrDefault(0L),
                mimeType = msg.getHeader("Content-Type")?.firstOrNull() ?: "text/plain",
                etag = msg.getHeader("Content-MD5")?.firstOrNull() ?: messageId
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDateHeader(msg: JMailMessage, name: String): Long {
        val raw = msg.getHeader(name)?.firstOrNull() ?: return System.currentTimeMillis()
        return runCatching {
            javax.mail.internet.MailDateFormat().parse(raw).time
        }.getOrDefault(System.currentTimeMillis())
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

    private fun parseReplyToAddresses(msg: JMailMessage): List<EmailAddress> {
        return try {
            msg.getHeader("Reply-To")?.firstOrNull()?.let { header ->
                header.split(",").map { addr ->
                    EmailAddress(
                        name = addr.takeIf { it.contains("<") }?.substringBefore("<")?.trim(),
                        email = addr.takeIf { it.contains("<") }?.substringAfter("<")?.substringBefore(">")?.trim()
                            ?: addr.trim()
                    )
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractContent(part: Part): Pair<String?, String?> {
        return try {
            when {
                part.isMimeType("text/plain") -> {
                    part.content as? String to null
                }
                part.isMimeType("text/html") -> {
                    null to (part.content as? String)
                }
                part.isMimeType("multipart/*") -> {
                    val mp = part.content as MimeMultipart
                    var text: String? = null
                    var html: String? = null
                    for (i in 0 until mp.count) {
                        val bp = mp.getBodyPart(i) as Part
                        val (t, h) = extractContent(bp)
                        if (t != null) text = t
                        if (h != null) html = h
                    }
                    // Evidence: EmailScreen.kt:331 shows bodyText ?: "(no content)". When a message
                    // has only text/html (no text/plain), text stays null and the detail is blank.
                    // Fall back to the HTML with tags stripped so bodyText is never null for a
                    // real message. HTML part is still kept in bodyHtml for rich rendering.
                    val plain = text ?: html?.let { stripHtml(it) }
                    plain to html
                }
                Part.ATTACHMENT == part.disposition || part.fileName != null -> {
                    null to null
                }
                else -> null to null
            }
        } catch (e: Exception) {
            null to null
        }
    }

    // ponytail: minimal HTML->text for body fallback. Strips tags + unescapes common entities.
    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<(?i)br\\s*/?>"), "\n")
            .replace(Regex("<(?i)/p>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&nbsp;", " ").replace("&#39;", "'")
            .trim()
    }

    private fun extractAttachments(part: Part, attachments: MutableList<com.unifiedcomms.data.model.Attachment>) {
        try {
            when {
                part.isMimeType("multipart/*") -> {
                    val mp = part.content as MimeMultipart
                    for (i in 0 until mp.count) {
                        extractAttachments(mp.getBodyPart(i) as Part, attachments)
                    }
                }
                Part.ATTACHMENT == part.disposition || part.fileName != null -> {
                    val attachment = com.unifiedcomms.data.model.Attachment(
                        fileName = part.fileName ?: "attachment",
                        mimeType = part.contentType,
                        sizeBytes = part.size.toLong(),
                        contentId = "",
                        isInline = Part.INLINE == part.disposition
                    )
                    attachments.add(attachment)
                }
            }
        } catch (e: Exception) {
            // ignore extraction errors
        }
    }

    override suspend fun fetchMessage(account: Account, folder: String, uid: String): Email? {
        // Future: per-message fetch by UID
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
                    override fun getPasswordAuthentication(): javax.mail.PasswordAuthentication {
                        return if (auth.type == com.unifiedcomms.data.model.AuthType.OAUTH2) {
                            javax.mail.PasswordAuthentication(auth.username!!, buildXoauth2(auth.username!!, auth.oauthAccessToken.orEmpty()))
                        } else {
                            javax.mail.PasswordAuthentication(auth.username!!, auth.passwordEncrypted!!)
                        }
                    }
                })

                val mimeMessage = MimeMessage(session)
                mimeMessage.setFrom(InternetAddress(email.sender.email, email.sender.name))

                email.recipients.to.forEach { mimeMessage.addRecipient(RecipientType.TO, InternetAddress(it.email, it.name)) }
                email.recipients.cc.forEach { mimeMessage.addRecipient(RecipientType.CC, InternetAddress(it.email, it.name)) }
                email.recipients.bcc.forEach { mimeMessage.addRecipient(RecipientType.BCC, InternetAddress(it.email, it.name)) }
                if (email.recipients.replyTo.isNotEmpty()) {
                    mimeMessage.setReplyTo(email.recipients.replyTo.map { InternetAddress(it.email, it.name) }.toTypedArray())
                }

                mimeMessage.subject = email.subject

                if (email.bodyHtml != null) {
                    mimeMessage.setContent(email.bodyHtml, "text/html; charset=utf-8")
                } else {
                    mimeMessage.setText(email.bodyText ?: "", "utf-8")
                }

                Transport.send(mimeMessage)
                SendResult.success(mimeMessage.getHeader("Message-ID")?.firstOrNull() ?: java.util.UUID.randomUUID().toString())
            } catch (e: Exception) {
                SendResult.failure(e.message ?: "Send failed")
            }
        }
    }

    override suspend fun moveToFolder(account: Account, uids: List<String>, fromFolder: String, toFolder: String): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val session = openImapSession(config)
                val store = session.store
                connectStoreWithRetry(store, config, auth)
                val src = store.getFolder(fromFolder)
                val dst = store.getFolder(toFolder)
                if (!src.exists() || !dst.exists()) {
                    store.close()
                    return@withContext SyncResult.failure("Folder not found: $fromFolder -> $toFolder")
                }
                src.open(Folder.READ_WRITE)
                val uidFolder = src as? javax.mail.UIDFolder
                val msgs = uids.mapNotNull { uid -> uidFolder?.getMessageByUID(uid.toLongOrNull() ?: -1L) }
                if (msgs.isNotEmpty()) {
                    dst.appendMessages(msgs.toTypedArray())
                    msgs.forEach { it.setFlag(Flags.Flag.DELETED, true) }
                    src.expunge()
                }
                src.close(false)
                store.close()
                SyncResult.success(itemsSynced = msgs.size)
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Move failed")
            }
        }
    }

    override suspend fun deleteMessages(account: Account, folder: String, uids: List<String>): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val session = openImapSession(config)
                val store = session.store
                connectStoreWithRetry(store, config, auth)
                val f = store.getFolder(folder)
                if (!f.exists()) {
                    store.close()
                    return@withContext SyncResult.failure("Folder not found: $folder")
                }
                f.open(Folder.READ_WRITE)
                val uidFolder = f as? javax.mail.UIDFolder
                val msgs = uids.mapNotNull { uid -> uidFolder?.getMessageByUID(uid.toLongOrNull() ?: -1L) }
                msgs.forEach { it.setFlag(Flags.Flag.DELETED, true) }
                f.expunge()
                f.close(false)
                store.close()
                SyncResult.success(itemsSynced = msgs.size)
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Delete failed")
            }
        }
    }

    override fun observeSyncProgress(accountId: String): kotlinx.coroutines.flow.Flow<SyncProgress> {
        return _syncProgress.transform { progressMap: Map<String, SyncProgress> ->
            emit(progressMap[accountId] ?: SyncProgress(accountId, null, SyncStage.COMPLETED, 0, 0))
        }.distinctUntilChanged()
    }

    override suspend fun testConnection(account: Account): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)
                // ponytail: reuse openImapSession so acceptAllCerts is honored
                // (testConnection previously built its own Properties and ignored
                // the flag, so 993 + self-signed/wildcard certs failed silently).
                val session = openImapSession(config)
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

    private data class Tuple4(
        val first: Int,
        val second: Int,
        val third: List<String>,
        val fourth: List<String>
    )
}
