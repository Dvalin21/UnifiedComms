package com.unifiedcomms.sync

import android.content.Context
import android.util.Log
import com.unifiedcomms.UnifiedCommsApplication
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.stripHtml
import com.unifiedcomms.data.model.stripCalendar
import com.unifiedcomms.data.model.EmailAddress
import com.unifiedcomms.data.model.EmailFlags
import com.unifiedcomms.data.model.EmailRecipients
import com.unifiedcomms.data.model.SystemLabels
import com.unifiedcomms.data.repository.EmailRepository
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.security.CryptoManager
import com.unifiedcomms.util.NotificationHelper
import com.unifiedcomms.util.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.activation.CommandMap
import javax.activation.MailcapCommandMap
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.mail.Session
import javax.mail.Store
import javax.mail.Folder
import javax.mail.Message as JMailMessage
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication
import javax.mail.Message.RecipientType
import javax.mail.Part
import javax.mail.Multipart
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeMessage
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.search.HeaderTerm
import javax.mail.search.OrTerm
import kotlinx.datetime.Clock

internal data class ImapMessageIdentity(val imapUid: String, val messageId: String)

internal fun ensureMailMimeHandlers() {
    val commandMap = CommandMap.getDefaultCommandMap() as? MailcapCommandMap ?: return
    commandMap.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain")
    commandMap.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html")
    commandMap.addMailcap("text/calendar;; x-java-content-handler=com.sun.mail.handlers.text_plain")
    commandMap.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822")
    commandMap.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed")
    CommandMap.setDefaultCommandMap(commandMap)
}

internal fun matchesLegacyFolderMessage(
    row: com.unifiedcomms.data.db.dao.EmailSyncUid,
    serverMessages: Set<ImapMessageIdentity>
): Boolean {
    val messageId = row.messageId?.trim().orEmpty()
    if (messageId.isBlank()) return false
    return serverMessages.any { it.messageId.trim() == messageId }
}

class EmailSyncEngineImpl(
    private val emailRepo: EmailRepository,
    private val accountRepo: AccountRepository,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope,
    private val calendarRepo: CalendarRepository? = null
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
                        totalFailed++
                        continue
                    }
                    val folderResult = syncSingleFolder(account, folder)
                    totalSynced += folderResult.first
                    totalFailed += folderResult.second
                    newItems.addAll(folderResult.third)
                    updatedItems.addAll(folderResult.fourth)
                }

                store?.close()

                if (totalFailed > 0) {
                    updateProgress(account.id, folder = null, SyncStage.ERROR, totalSynced, totalSynced)
                    return@withContext SyncResult.failure(
                        "$totalFailed configured folder/message operations failed",
                        totalFailed
                    )
                }

                updateProgress(account.id, folder = null, SyncStage.COMPLETED, totalSynced, totalSynced)
                return@withContext SyncResult.success(
                    itemsSynced = totalSynced,
                    newItems = newItems,
                    updatedItems = updatedItems,
                    deletedItems = deletedItems
                )

            } catch (e: CancellationException) {
                throw e
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
        val folderName = folder.fullName
        folder.open(Folder.READ_ONLY)

        // UID-based identity keeps reconnects from aliasing messages after
        // sequence-number changes. The server's full folder name is also the
        // local identity; JavaMail's leaf `name` breaks nested folders.
        val uidFolder = folder as? javax.mail.UIDFolder
        val serverUidValidity = uidFolder?.uidValidity?.toString()
        val MAX_INITIAL_MESSAGES = 300
        val startUid = if (uidFolder != null) {
            maxOf(1L, uidFolder.uidNext - MAX_INITIAL_MESSAGES)
        } else {
            null
        }
        val messageCount = folder.messageCount
        Log.d("EmailSyncEngineImpl", "folder=$folderName total=$messageCount uidValidity=$serverUidValidity")

        // Root-cause guard (K-9 proven): when UIDVALIDITY changes we must drop
        // local indexing for this folder; otherwise old imapUid rows collide
        // with new server UIDs and silently skip real mail.
        if (!serverUidValidity.isNullOrBlank()) {
            val localWithSameValidity = emailRepo.countByFolderAndUidValidity(account.id, folderName, serverUidValidity)
            if (localWithSameValidity == 0) {
                val anyLocalCount = emailRepo.getCount(account.id, folderName)
                if (anyLocalCount > 0) {
                    Log.w("EmailSyncEngineImpl", "folder=$folderName uidValidity changed, invalidating")
                    emailRepo.deleteByAccountAndFolder(account.id, folderName)
                }
            }
        }

        if (messageCount == 0) {
            reconcileDeletedMessages(account.id, folderName, serverUidValidity, startUid, emptySet())
            folder.close(false)
            return Tuple4(0, 0, emptyList(), emptyList())
        }

        val messages: Array<JMailMessage> = if (uidFolder != null) {
            val endUid = maxOf(0L, uidFolder.uidNext - 1)
            val firstUid = startUid ?: 1L
            if (endUid >= firstUid) uidFolder.getMessagesByUID(firstUid, endUid) else emptyArray()
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
        val serverUids = mutableSetOf<String>()
        val hadLocalMessagesBeforeSync = emailRepo.getCount(account.id, folderName) > 0
        val pendingFlagUpdates = mutableListOf<Pair<Email, com.unifiedcomms.data.model.EmailFlags>>()
        val fp = FetchProfile()
        fp.add(FetchProfile.Item.ENVELOPE)
        fp.add(FetchProfile.Item.FLAGS)
        fp.add("X-GM-LABELS")
        // ponytail: do NOT pre-fetch BODY.PEEK[]. Fetching the raw whole-message
        // blob makes msg.getContent() return an IMAPInputStream instead of a
        // parsed MimeMultipart, so every text part exposed its RAW source
        // (with its own MIME headers) and the stored body was garbage. Let
        // JavaMail parse the body on demand via getContent() — text parts then
        // decode cleanly. This is the canonical JavaMail IMAP body-read path.
        folder.fetch(messages, fp)
        val serverMessageIdentities = if (uidFolder != null) {
            messages.mapNotNull { msg ->
                val imapUid = uidFolder.getUID(msg).toString()
                msg.getHeader("Message-ID")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { ImapMessageIdentity(imapUid, it) }
            }.toSet()
        } else {
            emptySet()
        }
        val legacyRows = if (folder.fullName != folder.name) {
            runCatching { emailRepo.getSyncUids(account.id, folder.name) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val allServerMessageIdentities = findLegacyServerMessages(
            folder = folder,
            uidFolder = uidFolder,
            candidates = legacyRows,
            known = serverMessageIdentities
        )
        migrateLegacyFolderNames(
            accountId = account.id,
            fullName = folder.fullName,
            leafName = folder.name,
            serverUidValidity = serverUidValidity,
            serverMessages = allServerMessageIdentities
        )

        for (msg in messages) {
            if (!folder.isOpen) break
            try {
                val messageId = msg.getHeader("Message-ID")?.firstOrNull()
                val imapUid = uidFolder?.getUID(msg)?.toString()
                    ?: "$folderName#${msg.messageNumber}"
                val parsedEmail = parseEmail(msg, account.id, folderName, messageId, imapUid)
                val email = parsedEmail?.let { parsed ->
                    val response = parsed.invite
                    if (response?.method == com.unifiedcomms.data.model.CalendarInviteMessage.InviteMethod.REPLY) {
                        applyCalendarResponse(account.id, response)
                        parsed.copy(invite = null)
                    } else {
                        parsed
                    }
                }
                if (email != null) {
                    serverUids += imapUid
                    val stableUid = serverUidValidity ?: email.uidValidity
                    // ponytail: use the lightweight lookup (id/etag/flags only) so
                    // we never load a multi-MB bodyText into the CursorWindow —
                    // that overflow aborted syncs for large folders (Sent/Trash).
                    var localKey = emailRepo.getSyncKeyByImapUid(account.id, imapUid, folderName)
                    if (localKey == null) {
                        localKey = emailRepo.getSyncKeyByUid(account.id, email.uid, folderName)
                    }
                    if (localKey == null) {
                        emailRepo.insert(email.copy(uidValidity = stableUid, imapUid = imapUid))
                        newItems.add(email.id)
                        if (hadLocalMessagesBeforeSync && folderName.equals("INBOX", ignoreCase = true) && !email.flags.isRead &&
                            runCatching { PreferencesManager.getInstance().getBoolean("notif_email", true) }.getOrDefault(true)
                        ) {
                            runCatching {
                                val app = UnifiedCommsApplication.getInstance()
                                NotificationHelper.showEmailNotification(
                                    app,
                                    account.id,
                                    account.name,
                                    email.sender.name ?: email.sender.email,
                                    email.subject,
                                    email.getSnippet(200),
                                    email.messageId,
                                    emailRepo.getUnreadCount(account.id, folderName)
                                )
                            }
                        }
                    } else {
                        // A body can be absent/garbage on first sync yet valid on a
                        // later pass; etag/flags may be unchanged so we MUST still
                        // refresh the body. parseEmail now reliably returns a clean
                        // body, so always write it (never clobber with null).
                        val mergedFlags = if (localKey.flags != email.flags) localKey.flags else email.flags
                        emailRepo.updateSyncMeta(
                            id = localKey.id,
                            flags = mergedFlags,
                            labels = email.labels,
                            systemLabels = email.systemLabels,
                            etag = email.etag ?: "",
                            updatedAt = Clock.System.now().toEpochMilliseconds(),
                            messageId = email.messageId,
                            subject = email.subject,
                            bodyText = email.bodyText,
                            bodyHtml = email.bodyHtml,
                            preview = email.preview,
                            attachments = email.attachments,
                            invite = email.invite
                        )
                        // Push the flags that were in Room before this server refresh.
                        // `email` is the newly parsed server copy; passing it as both
                        // sides made the diff always empty and silently discarded
                        // local read/star changes.
                        pendingFlagUpdates.add(email.copy(id = localKey.id, flags = localKey.flags) to email.flags)

                        updatedItems.add(localKey.id)
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
            if (!applyLocalFlagsToServer(folder, local, serverFlags)) {
                emailRepo.update(local.copy(needsSync = true))
            }
        }

        if (parsedFail == 0 && totalFailed == 0) {
            reconcileDeletedMessages(account.id, folderName, serverUidValidity, startUid, serverUids)
        }

        if (folder.isOpen) {
            folder.close(false)
        }
        totalFailed += parsedFail
        Log.d("EmailSyncEngineImpl", "folder=$folderName done synced=$totalSynced failed=$totalFailed parsedFail=$parsedFail")
        return Tuple4(totalSynced, totalFailed, newItems, updatedItems)
    }

    private fun findLegacyServerMessages(
        folder: Folder,
        uidFolder: javax.mail.UIDFolder?,
        candidates: List<com.unifiedcomms.data.db.dao.EmailSyncUid>,
        known: Set<ImapMessageIdentity>
    ): Set<ImapMessageIdentity> {
        if (uidFolder == null) return known
        val messageIds = candidates.mapNotNull { it.messageId?.trim()?.takeIf(String::isNotBlank) }.distinct()
        if (messageIds.isEmpty()) return known
        return runCatching {
            val term = OrTerm(messageIds.map { HeaderTerm("Message-ID", it) }.toTypedArray())
            val matches = folder.search(term)
            if (matches.isEmpty()) return@runCatching known
            val profile = FetchProfile().apply { add(FetchProfile.Item.ENVELOPE) }
            folder.fetch(matches, profile)
            val wanted = messageIds.toSet()
            val found = matches.mapNotNull { message ->
                val imapUid = uidFolder.getUID(message).toString()
                message.getHeader("Message-ID")?.firstOrNull()?.trim()
                    ?.takeIf { it in wanted }
                    ?.let { ImapMessageIdentity(imapUid, it) }
            }.toSet()
            known + found
        }.getOrElse { error ->
            Log.w("EmailSyncEngineImpl", "legacy folder Message-ID search failed: ${error.message}")
            known
        }
    }

    // ponytail: rows without a Message-ID stay in the old folder; sequence-derived
    // UIDs cannot be safely distinguished when a leaf name is shared by two folders.
    internal suspend fun migrateLegacyFolderNames(
        accountId: String,
        fullName: String,
        leafName: String,
        serverUidValidity: String?,
        serverMessages: Set<ImapMessageIdentity>
    ) {
        if (fullName == leafName || serverUidValidity.isNullOrBlank() || serverMessages.isEmpty()) return
        val fullRows = runCatching { emailRepo.getSyncUids(accountId, fullName) }.getOrDefault(emptyList())
        val fullIds = fullRows.map { it.id }.toSet()
        val fullUids = fullRows.mapNotNull { it.imapUid }.toSet()
        val fullMessageIds = fullRows.mapNotNull { it.messageId?.trim() }.toSet()
        val legacyRows = runCatching { emailRepo.getSyncUids(accountId, leafName) }.getOrDefault(emptyList())
        val ambiguousMessageIds = legacyRows
            .mapNotNull { it.messageId?.trim()?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        legacyRows
            .filter { it.id !in fullIds && it.imapUid !in fullUids && it.messageId?.trim() !in fullMessageIds }
            .filter { it.messageId?.trim() !in ambiguousMessageIds }
            .filter { matchesLegacyFolderMessage(it, serverMessages) }
            .forEach { key ->
                val identity = serverMessages.firstOrNull { candidate ->
                    candidate.messageId.trim() == key.messageId?.trim() &&
                        (key.imapUid == candidate.imapUid || key.imapUid?.toLongOrNull() == null)
                } ?: return@forEach
                emailRepo.getById(key.id)?.let {
                    emailRepo.update(
                        it.copy(
                            folder = fullName,
                            uid = identity.imapUid,
                            messageId = identity.messageId,
                            uidValidity = serverUidValidity,
                            imapUid = identity.imapUid
                        )
                    )
                }
            }
    }

    private suspend fun reconcileDeletedMessages(
        accountId: String,
        folderName: String,
        uidValidity: String?,
        startUid: Long?,
        serverUids: Set<String>
    ) {
        if (uidValidity.isNullOrBlank() || startUid == null) return
        runCatching { emailRepo.getSyncUids(accountId, folderName) }.getOrDefault(emptyList())
            .filter { it.uidValidity == uidValidity }
            .forEach { local ->
                val uid = local.imapUid?.toLongOrNull() ?: return@forEach
                if (uid >= startUid && uid.toString() !in serverUids) {
                    emailRepo.deleteById(local.id)
                }
            }
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
        // ponytail: a single connect is hard-bounded at 30s via timedConnect (the
        // blocking JavaMail call ignores coroutine cancellation). Retry once.
        val perAttemptMs = 30_000L
        var lastErr: Throwable? = null
        for (attempt in 1..2) {
            try {
                timedConnect(store, config.imapHost, config.imapPort, user, pass, perAttemptMs)
                return
            } catch (e: javax.mail.MessagingException) {
                lastErr = e
            }
        }
        throw lastErr ?: javax.mail.MessagingException("IMAP connect failed")
    }

    private suspend fun applyLocalFlagsToServer(
        folder: Folder,
        local: Email,
        serverFlags: com.unifiedcomms.data.model.EmailFlags
    ): Boolean {
        // Caller must open `folder` in READ_WRITE before invoking this.
        if (local.flags == serverFlags) return true
        return try {
            val uidFolder = folder as? javax.mail.UIDFolder ?: return false
            val uidVal = local.imapUid?.toLongOrNull() ?: return false
            val msg = uidFolder.getMessageByUID(uidVal) ?: return false
            if (local.flags.isRead != serverFlags.isRead) msg.setFlag(Flags.Flag.SEEN, local.flags.isRead)
            if (local.flags.isFlagged != serverFlags.isFlagged) msg.setFlag(Flags.Flag.FLAGGED, local.flags.isFlagged)
            if (local.flags.isAnswered != serverFlags.isAnswered) msg.setFlag(Flags.Flag.ANSWERED, local.flags.isAnswered)
            true
        } catch (e: Exception) {
            Log.w("EmailSyncEngineImpl", "flag sync failed uid=${local.imapUid} folder=${local.folder}: ${e.message}")
            false
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

    private fun openImapSession(
        config: com.unifiedcomms.data.model.ServerConfig,
        connectTimeoutMs: Int = 60000,
        readTimeoutMs: Int = 300000
    ): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", config.imapHost)
            put("mail.imap.port", config.imapPort)
            put("mail.imap.ssl.enable", config.imapUseSsl)
            put("mail.imap.auth", true)
            put("mail.imap.connectiontimeout", connectTimeoutMs)
            put("mail.imap.timeout", readTimeoutMs)
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

    // ponytail: a blocking JavaMail store.connect() is NOT cancellable by Kotlin
    // coroutine cancellation — the OS-level TCP connect to a dead/half-open host
    // ignores interrupts, so withTimeout() waits the full hang (observed 120s on a
    // black-hole host) before the child acknowledges. The ONLY hard bound is on the
    // blocking result, via Future.get(timeout). We run the connect on a dedicated
    // pool and cap it; get() returns at the bound no matter what the thread does.
    // The thread is left running (daemon) and GC'd; it cannot wedge the caller.
    private val connectExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "imap-connect").apply { isDaemon = true }
    }

    private fun timedConnect(
        store: Store,
        host: String?,
        port: Int? = null,
        user: String,
        pass: String,
        timeoutMs: Long
    ) {
        val future = connectExecutor.submit(Callable {
            if (port != null) store.connect(host, port, user, pass)
            else store.connect(host, user, pass)
        })
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            // ponytail: do NOT call store.close() here — close() blocks until the
            // uncancellable OS connect finishes (observed +105s), which would make
            // THIS method hang past its own timeout and defeat the whole fix. The
            // connect runs on a daemon thread; future.cancel(true) signals it and the
            // JVM reclaims it. We return immediately at the bound.
            future.cancel(true)
            throw javax.mail.MessagingException("IMAP connect timed out after ${timeoutMs}ms", e)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
    }

    /**
     * Decode an RFC 2047 encoded-word subject, handling adjacent encoded words
     * with no inter-word whitespace (e.g. "...?==?utf-8...") that JavaMail's
     * getter and MimeUtility.decodeText leave partially raw. Each =?..?= token is
     * decoded individually and concatenated with the literal text between them.
     */
    private fun decodeRfc2047Subject(raw: String): String {
        if (!raw.contains("=?")) return raw
        val sb = StringBuilder()
        val regex = Regex("=\\?[^?]+\\?[qQbB]\\?[^?]*\\?=")
        var last = 0
        for (m in regex.findAll(raw)) {
            sb.append(raw.substring(last, m.range.first))
            sb.append(runCatching { javax.mail.internet.MimeUtility.decodeWord(m.value) }.getOrDefault(m.value))
            last = m.range.last + 1
        }
        sb.append(raw.substring(last))
        return sb.toString().trim()
    }

    private suspend fun applyCalendarResponse(
        accountId: String,
        response: com.unifiedcomms.data.model.CalendarInviteMessage
    ) {
        val repo = calendarRepo ?: return
        val existing = repo.getEventByUid(response.eventUid, accountId) ?: return
        val coloredExisting = InviteMapper.applyInviteColor(existing, response.color)
        val changedAttendee = response.attendees.firstOrNull {
            it.status != com.unifiedcomms.data.model.AttendeeStatus.NEEDS_ACTION &&
                !it.email.equals(response.organizerEmail, ignoreCase = true)
        } ?: return
        val updated = coloredExisting.copy(
            attendees = coloredExisting.attendees.map { attendee ->
                if (attendee.email.equals(changedAttendee.email, ignoreCase = true)) {
                    attendee.copy(
                        status = changedAttendee.status,
                        respondedAt = kotlinx.datetime.Clock.System.now(),
                        comment = changedAttendee.comment
                    )
                } else {
                    attendee
                }
            },
            updatedAt = kotlinx.datetime.Clock.System.now(),
            needsSync = false
        )
        if (updated != existing) repo.updateEvent(updated)
    }

    private fun parseEmail(msg: JMailMessage, accountId: String, folder: String, messageId: String?, uid: String): Email? {
        val email = try {
            val uid = uid
            val threadId = msg.getHeader("X-GM-THRID")?.firstOrNull() ?: messageId ?: uid
            val inReplyTo = msg.getHeader("In-Reply-To")?.firstOrNull()
            val references = msg.getHeader("References")?.toList() ?: emptyList()

            val fromAddrs = runCatching { msg.from }.getOrNull()
            val fromAddr = (fromAddrs?.firstOrNull() as? javax.mail.internet.InternetAddress)
            val sender = EmailAddress(
                name = fromAddr?.personal?.takeIf { it.isNotBlank() },
                email = fromAddr?.address?.takeIf { it.isNotBlank() }
                    ?: msg.getHeader("From")?.firstOrNull()?.takeIf { it.contains("<") }?.substringAfter("<")?.substringBefore(">")?.trim()
                    ?: msg.getHeader("From")?.firstOrNull()?.trim() ?: ""
            )

            val recipients = EmailRecipients(
                to = parseAddresses(msg, RecipientType.TO),
                cc = parseAddresses(msg, RecipientType.CC),
                bcc = parseAddresses(msg, RecipientType.BCC),
                replyTo = parseReplyToAddresses(msg)
            )

            // ponytail: decode RFC2047 subjects robustly. msg.subject (the getter) and
            // MimeUtility.decodeText both bail on adjacent encoded words with no
            // whitespace between them (?==?), leaking raw =?utf-8?q? into the UI.
            // Decode each encoded word individually via regex and concatenate, so
            // servers that omit the inter-word space still render correctly.
            val rawSubject = msg.getHeader("Subject")?.firstOrNull() ?: ""
            val subject = decodeRfc2047Subject(rawSubject)
            val sentAt = parseDateHeader(msg, "Date")
            val receivedAt = parseDateHeader(msg, "Received") ?: sentAt

            // ponytail: capture the raw RFC822 bytes ONCE. extractContent(parsedMsg)
            // consumes parsedMsg's stream, so attachment extraction must use these
            // same bytes (re-readable) — not parsedMsg.inputStream, which would be
            // spent. This is also what makes the boundary-aware PDF fix reliable.
            val rawBytes = runCatching { msg.getInputStream().readBytes() }.getOrElse { ByteArray(0) }
            val parsedMsg = runCatching { MimeMessage(null, rawBytes.inputStream()) }.getOrElse { msg }
            val (bodyTextRaw, bodyHtml) = runCatching { extractContent(parsedMsg) }.getOrDefault(null to null)
            // ponytail: never store a raw MIME blob as the body. If the plain
            // text came back blank/raw but we have HTML, render it to text. If
            // both are blank, leave null (UI shows "(no content)" + subject) rather
            // than leaking MIME headers into the body.
            val bodyText = if (!bodyTextRaw.isNullOrBlank()) bodyTextRaw else bodyHtml?.let { stripHtml(it) }
            val attachments = mutableListOf<com.unifiedcomms.data.model.Attachment>()
            // ponytail: use the captured raw bytes (re-readable) so the boundary-aware
            // parser sees the full MIME tree even after extractContent consumed the
            // parsed message stream. msg.content returns an IMAPInputStream (not a
            // MimeMultipart), which is why we never call extractAttachments(msg).
            runCatching { extractAttachmentsFromBytes(rawBytes, attachments) }

            // ponytail: extract an embedded calendar invite (text/calendar). SOGo/Edison-style
            // invites inline the VCALENDAR inside the HTML body, so scan bodyText+bodyHtml for
            // BEGIN:VCALENDAR rather than relying on a separate MIME part (line 877 notes
            // text/calendar is not treated as an attachment). Parse the first VEVENT and store
            // it on the email; EmailDetailScreen renders it as an InviteCard.
            val parsedInvite = runCatching {
                val haystack = "${bodyText ?: ""}\n${bodyHtml ?: ""}"
                val start = haystack.indexOf("BEGIN:VCALENDAR", ignoreCase = true)
                if (start >= 0) {
                    val end = haystack.indexOf("END:VCALENDAR", ignoreCase = true)
                    val ics = if (end > start) haystack.substring(start, end + "END:VCALENDAR".length) else null
                    ics?.let {
                        val method = if (ics.lineSequence().any { line -> line.trim().startsWith("METHOD:REPLY", ignoreCase = true) }) {
                            com.unifiedcomms.data.model.CalendarInviteMessage.InviteMethod.REPLY
                        } else {
                            com.unifiedcomms.data.model.CalendarInviteMessage.InviteMethod.REQUEST
                        }
                        ICalParser.parse(it, accountId, "", "invite-${uid}").events
                            .firstOrNull()
                            ?.let { ev -> InviteMapper.toInviteMessage(ev, method) }
                    }
                } else null
            }.getOrNull()
            val isReply = parsedInvite?.method == com.unifiedcomms.data.model.CalendarInviteMessage.InviteMethod.REPLY
            val invite = parsedInvite
            val displayBodyText = if (isReply) bodyText?.stripCalendar() else bodyText

            // ponytail: strip the embedded VCALENDAR blob before storing the preview,
            // so the inbox row never shows raw "BEGIN:VCALENDAR...". getSnippet() also
            // strips defensively, but the stored value must be clean.
            val preview = displayBodyText?.stripHtml()?.take(200) ?: subject
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
                bodyText = displayBodyText,
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
                etag = msg.getHeader("Content-MD5")?.firstOrNull() ?: messageId,
                invite = invite
            )
        } catch (e: Exception) {
            null
        }
        return email
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

    // ponytail: IMAP-fetched text parts return an InputStream (com.sun.mail.imap
    // .IMAPInputStream / ByteArrayInputStream), NOT a String. The old code did
    // `part.content as? String` which is null for a stream -> the body was silently
    // dropped -> EmailDetailScreen showed "(no content)" for every real message.
    // Read the stream with the part's own charset. Same root cause class as the
    // chat IMAPInputStream bug.
    private fun partCharset(part: Part): java.nio.charset.Charset {
        val ct = runCatching { part.contentType }.getOrNull() ?: return java.nio.charset.StandardCharsets.UTF_8
        val m = Regex("charset=[\"']?([^\"';\\s]+)", RegexOption.IGNORE_CASE).find(ct)
        return m?.let { runCatching { java.nio.charset.Charset.forName(it.groupValues[1]) }.getOrNull() }
            ?: java.nio.charset.StandardCharsets.UTF_8
    }

    private fun readText(part: Part): String? = try {
        val c = part.content
        val text = when (c) {
            is String -> c
            // ponytail: a fetched IMAP text part arrives as a decoded stream
            // (e.g. com.sun.mail.util.QPDecoderStream). Read it with an explicit
            // byte-loop that only stops at read()==-1; do NOT use
            // bufferedReader().readText(), which stops early on these streams.
            is java.io.InputStream -> {
                val out = java.io.ByteArrayOutputStream()
                val tmp = ByteArray(8192)
                var n: Int
                while (c.read(tmp).also { n = it } != -1) out.write(tmp, 0, n)
                out.toString(partCharset(part).name())
            }
            else -> c?.toString()
        }
        // ponytail: when a part's content was fetched as the raw MIME source
        // (headers + blank line + body), strip the header block so the stored
        // body is the actual message text, not "--boundary Content-Type: ...".
        stripMimeHeaders(text)?.trim()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun stripMimeHeaders(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        // Only touch content that actually begins with a MIME header / boundary.
        val trimmed = raw.trimStart()
        val looksLikeHeaders = trimmed.startsWith("--") ||
            trimmed.startsWith("Content-Type", ignoreCase = true) ||
            trimmed.startsWith("MIME-Version", ignoreCase = true) ||
            trimmed.startsWith("Content-Transfer-Encoding", ignoreCase = true)
        if (!looksLikeHeaders) return raw
        val idx = raw.indexOf("\r\n\r\n")
        val idx2 = raw.indexOf("\n\n")
        val sep = when {
            idx >= 0 && idx2 >= 0 -> minOf(idx, idx2)
            idx >= 0 -> idx
            idx2 >= 0 -> idx2
            else -> -1
        }
        return if (sep >= 0) raw.substring(sep + if (raw[sep] == '\r') 4 else 2) else raw
    }

    /**
     * ponytail: fallback for when JavaMail's getContent() exposes the WHOLE
     * raw multipart blob (headers + boundaries + every part) instead of a
     * parsed tree. Split the blob on its boundary delimiter and extract the
     * first text/plain part's body (text/html as a fallback, tags stripped).
     * This is the last-resort path that guarantees a real body for messages
     * whose structure JavaMail mis-decodes on Android.
     */
    private fun parseRawMultipart(raw: String): Pair<String?, String?> {
        val lines = raw.split("\r\n", "\n")
        // ponytail: the REAL MIME boundary is a "--<token>" line whose NEXT
        // non-empty line is a part header (Content-Type/MIME-Version/...). A
        // forwarded-message marker ("-------- Original Message --------") also
        // starts with "--" but is NOT a boundary — picking it as the delimiter
        // makes the parser miss every real part. So scan for the true boundary.
        var delimiter: String? = null
        for (i in lines.indices) {
            val ln = lines[i]
            if (ln.startsWith("--") && ln.length > 4) {
                val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() }?.lowercase().orEmpty()
                if (next.startsWith("content-type") || next.startsWith("mime-version") ||
                    next.startsWith("content-transfer-encoding")
                ) {
                    delimiter = ln
                    break
                }
            }
        }
        if (delimiter == null) {
            // No real MIME boundary found (e.g. a forwarded-quote marker with a
            // plain quoted body). Cut the body at the first leaked boundary line
            // if present; otherwise return the text as-is.
            val cut = raw.indexOf("\r\n--")
            val cut2 = raw.indexOf("\n--")
            val cutAt = when {
                cut >= 0 && cut2 >= 0 -> minOf(cut, cut2)
                cut >= 0 -> cut
                cut2 >= 0 -> cut2
                else -> -1
            }
            val body = if (cutAt >= 0) raw.substring(0, cutAt).trim() else raw.trim()
            return (body to null)
        }
        // Segments between delimiter lines are the parts (drop the preamble;
        // skip the closing "--delimiter--" which also starts with the delimiter).
        val segments = raw.split(delimiter).drop(1).filter { !it.startsWith("--") }
        var text: String? = null
        var html: String? = null
        for (seg in segments) {
            val hb = seg.indexOf("\r\n\r\n")
            val sep = if (hb >= 0) hb + 4 else seg.indexOf("\n\n").let { if (it >= 0) it + 2 else -1 }
            if (sep < 0) continue
            val headers = seg.substring(0, sep).lowercase()
            // ponytail: a part with no Content-Type is plain text (e.g. a
            // forwarded message body before the first boundary).
            val isPlain = !headers.contains("content-type") || headers.contains("content-type: text/plain")
            val isHtml = headers.contains("content-type: text/html")
            // A part body can itself contain a nested boundary; cut at it so we
            // don't leak trailing MIME into the extracted text.
            val bodyRaw = seg.substring(sep)
            val bIdx = bodyRaw.indexOf("\n--")
            val bIdxR = bodyRaw.indexOf("\r\n--")
            val cut = when {
                bIdxR >= 0 && bIdx >= 0 -> minOf(bIdxR, bIdx)
                bIdxR >= 0 -> bIdxR
                bIdx >= 0 -> bIdx
                else -> -1
            }
            val body = if (cut >= 0) bodyRaw.substring(0, cut).trim() else bodyRaw.trim()
            if (text == null && isPlain) text = body
            else if (html == null && isHtml) html = body
        }
        val plain = text ?: html?.let { stripHtml(it) }
        return (plain to html)
    }

    private fun looksLikeRawMultipart(s: String): Boolean {
        // A decoded body never starts with a MIME boundary / part header.
        val t = s.trimStart()
        return t.startsWith("--") && t.length > 4
    }

    private fun extractContent(part: Part): Pair<String?, String?> {
        return try {
            when {
                part.isMimeType("text/plain") -> {
                    val t = readText(part)
                    if (t != null && looksLikeRawMultipart(t)) {
                        // ponytail: never leak the raw blob. parseRawMultipart may
                        // return null for oddly-structured parts; fall back to
                        // stripping the leading MIME header block, then the html
                        // sibling is handled by parseEmail's bodyText safety net.
                        val fixed = parseRawMultipart(t).first
                        (fixed ?: stripMimeHeaders(t)) to null
                    } else t to null
                }
                part.isMimeType("text/html") -> {
                    val h = readText(part)
                    if (h != null && looksLikeRawMultipart(h)) {
                        null to (parseRawMultipart(h).second ?: stripMimeHeaders(h))
                    } else null to h
                }
                part.isMimeType("text/calendar") -> {
                    // iTIP invites are proper text/calendar parts, not inline HTML.
                    // Preserve the decoded payload so parseEmail can build InviteCard.
                    readText(part) to null
                }
                part.isMimeType("message/rfc822") -> {
                    // ponytail: a forwarded/attached email is itself a Part
                    // (MimeMessage). Recurse into it so the inline forward's body
                    // surfaces instead of being treated as an opaque attachment.
                    val nested = part.content as? Part
                    if (nested != null) extractContent(nested) else (null to null)
                }
                part.isMimeType("multipart/*") -> {
                    // ponytail: a BODY.PEEK[] fetch returns the raw multipart bytes
                    // as an IMAPInputStream, NOT a parsed MimeMultipart — so a blind
                    // `as MimeMultipart` (old code) threw and discarded the whole
                    // body, and `as? MimeMultipart` is always null (no recursion,
                    // empty body). Build the MimeMultipart from the stream so we can
                    // walk the real parts. This is the canonical JavaMail IMAP fix.
                    val mp = when (val c = part.content) {
                        is MimeMultipart -> c
                        is java.io.InputStream -> runCatching {
                            val ct = runCatching { part.contentType }.getOrNull() ?: "multipart/mixed"
                            val raw = c as java.io.InputStream
                            val ds = object : javax.activation.DataSource {
                                override fun getInputStream(): java.io.InputStream = raw
                                override fun getOutputStream() = java.io.ByteArrayOutputStream()
                                override fun getContentType() = ct
                                override fun getName() = "multipart"
                            }
                            MimeMultipart(ds)
                        }.getOrNull()
                        else -> null
                    }
                    var text: String? = null
                    var html: String? = null
                    if (mp != null) {
                        for (i in 0 until mp.count) {
                            val bp = mp.getBodyPart(i) as? Part ?: continue
                            val (t, h) = runCatching { extractContent(bp) }.getOrDefault(null to null)
                            if (t != null) text = t
                            if (h != null) html = h
                        }
                    } else {
                        // ponytail: JavaMail exposed the raw multipart blob instead
                        // of a parsed tree. Read it as text and split on boundaries.
                        val rawText = runCatching { readText(part) }.getOrNull()
                        if (rawText != null) {
                            val (rt, rh) = parseRawMultipart(rawText)
                            if (rt != null) text = rt
                            if (rh != null) html = rh
                        }
                    }
                    // EmailScreen shows bodyText ?: "(no content)". When a message
                    // has only text/html (no text/plain), fall back to the HTML with
                    // tags stripped so bodyText is never null for a real message.
                    // Also guard: if the walk still produced a raw-blob string
                    // (boundary markers present), run the raw split as a safety net.
                    val safeText = if (text != null && looksLikeRawMultipart(text)) {
                        parseRawMultipart(text)?.first ?: text
                    } else text
                    val plain = safeText ?: html?.let { stripHtml(it) }
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

    /**
     * Build a [MimeMultipart] from a raw part stream using the REAL outer MIME
     * boundary, forcing type multipart/mixed. JavaMail's default parser trusts
     * the part's Content-Type header; for mislabeled messages (real multipart/mixed
     * whose top header says multipart/alternative) that drops sibling parts like a
     * PDF attachment. Parsing by the actual boundary — like SOGo/Edison do — finds
     * them. Returns null if no boundary can be located.
     */
    private fun boundaryAwareMultipart(stream: java.io.InputStream): MimeMultipart? {
        return runCatching {
            val bytes = stream.readBytes()
            val text = String(bytes, Charsets.ISO_8859_1)
            // The outer boundary is the first line beginning with "--".
            val boundary = text.lineSequence().firstNotNullOfOrNull { line ->
                val t = line.trim()
                if (t.startsWith("--") && t.length > 4) t.substring(2).trim().takeIf { it.isNotBlank() } else null
            } ?: return null
            val ds = object : javax.activation.DataSource {
                override fun getInputStream(): java.io.InputStream = bytes.inputStream()
                override fun getOutputStream() = java.io.ByteArrayOutputStream()
                override fun getContentType() = "multipart/mixed; boundary=\"$boundary\""
                override fun getName() = "multipart"
            }
            MimeMultipart(ds)
        }.getOrNull()
    }

    /**
     * Entry point for the sync path. Builds the top-level MimeMultipart from the
     * already-captured raw bytes (re-readable even after extractContent consumed
     * the parsed message stream) and recurses with [extractAttachments].
     */
    private fun extractAttachmentsFromBytes(bytes: ByteArray, attachments: MutableList<com.unifiedcomms.data.model.Attachment>) {
        if (bytes.isEmpty()) return
        val mp = boundaryAwareMultipart(bytes.inputStream()) ?: return
        for (i in 0 until mp.count) {
            extractAttachments(mp.getBodyPart(i) as Part, attachments)
        }
    }

    private fun extractAttachments(part: Part, attachments: MutableList<com.unifiedcomms.data.model.Attachment>) {
        try {
            when {
                part.isMimeType("multipart/*") -> {
                    // ponytail: ROOT CAUSE (confirmed on lghtshine "tax return"): the
                    // message is really multipart/mixed (outer boundary wraps an
                    // alternative + a PDF), but the sender MISLABELS the top
                    // Content-Type as multipart/alternative. JavaMail trusts that
                    // header and only parses the alternative's leaves, silently
                    // dropping the sibling PDF — while SOGo/Edison parse by BOUNDARY
                    // and show it. Fix: build the MimeMultipart from the REAL outer
                    // boundary as multipart/mixed, ignoring the broken header.
                    val mp = boundaryAwareMultipart(part.inputStream)
                    if (mp != null) {
                        for (i in 0 until mp.count) {
                            extractAttachments(mp.getBodyPart(i) as Part, attachments)
                        }
                    }
                }
                // ponytail: forwarded/embedded message can wrap an attachment.
                part.isMimeType("message/rfc822") -> {
                    val nested = runCatching { part.content as? MimeMessage }.getOrNull()
                        ?: runCatching { MimeMessage(null, part.inputStream) }.getOrNull()
                    nested?.let { extractAttachments(it, attachments) }
                }
                else -> {
                    // ponytail: a part is an attachment if it carries an explicit
                    // attachment disposition, has a filename, OR is a binary payload
                    // (application/*, image/*, audio/*, video/*) — some senders ship
                    // PDFs as inline;filename=... or with no disposition at all. The
                    // old code only matched disposition/filename and silently dropped
                    // those. text/calendar (meeting replies) is NOT an attachment.
                    val ct = runCatching { part.contentType }.getOrNull().orEmpty().lowercase()
                    val isBinary = ct.startsWith("application/") ||
                        (ct.startsWith("image/") && !ct.contains("cid")) ||
                        ct.startsWith("audio/") || ct.startsWith("video/")
                    if (Part.ATTACHMENT == part.disposition || part.fileName != null || isBinary) {
                        val attachment = com.unifiedcomms.data.model.Attachment(
                            fileName = part.fileName ?: runCatching { part.contentType.substringAfter("name=").trim('"', '\'') }.getOrNull()
                                ?: "attachment",
                            mimeType = part.contentType,
                            sizeBytes = runCatching { part.size.toLong() }.getOrNull() ?: 0L,
                            contentId = runCatching { (part as? javax.mail.internet.MimeBodyPart)?.contentID }
                                .getOrNull()?.trim('<', '>').orEmpty(),
                            isInline = Part.INLINE == part.disposition
                        )
                        attachments.add(attachment)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore extraction errors
        }
    }

    override suspend fun fetchMessage(account: Account, folder: String, uid: String): Email? {
        return withContext(Dispatchers.IO) {
            var store: Store? = null
            var imapFolder: Folder? = null
            try {
                val numericUid = uid.toLongOrNull() ?: return@withContext null
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)
                store = openImapSession(config).store
                connectStoreWithRetry(store!!, config, auth)
                imapFolder = store!!.getFolder(folder)
                imapFolder!!.open(Folder.READ_ONLY)
                val message = (imapFolder as javax.mail.UIDFolder).getMessageByUID(numericUid)
                    ?: return@withContext null
                parseEmail(
                    message,
                    account.id,
                    imapFolder!!.fullName,
                    message.getHeader("Message-ID")?.firstOrNull(),
                    uid
                )
            } catch (e: Exception) {
                Log.w("EmailSyncEngineImpl", "fetchMessage failed folder=$folder uid=$uid: ${e.message}")
                null
            } finally {
                runCatching { imapFolder?.close(false) }
                runCatching { store?.close() }
            }
        }
    }

    override suspend fun fetchAttachment(
        account: Account,
        folder: String,
        uid: String,
        attachment: com.unifiedcomms.data.model.Attachment
    ): String? = withContext(Dispatchers.IO) {
        var store: javax.mail.Store? = null
        var imapFolder: javax.mail.Folder? = null
        try {
            val maxBytes = account.syncConfig.maxAttachmentSizeMb.toLong().coerceAtLeast(1L) * 1024L * 1024L
            if (attachment.sizeBytes > 0L && attachment.sizeBytes > maxBytes) {
                Log.w("EmailSyncEngineImpl", "Attachment exceeds account limit: ${attachment.fileName}")
                return@withContext null
            }
            val config = account.serverConfig
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val props = Properties().apply {
                put("mail.store.protocol", "imap")
                put("mail.imap.host", config.imapHost)
                put("mail.imap.port", config.imapPort.toString())
                put("mail.imap.ssl.enable", "true")
                put("mail.imap.auth", "true")
                put("mail.imap.timeout", "30000")
                put("mail.imap.connectiontimeout", "30000")
                put("mail.imap.writetimeout", "30000")
            }
            val session = javax.mail.Session.getInstance(props, null)
            store = session.getStore("imap") as com.sun.mail.imap.IMAPStore
            connectStoreWithRetry(store, config, auth)
            imapFolder = store.getFolder(folder)
            imapFolder.open(javax.mail.Folder.READ_ONLY)
            val uidFolder = imapFolder as? javax.mail.UIDFolder ?: return@withContext null
            val msg = uidFolder.getMessageByUID(uid.toLongOrNull() ?: return@withContext null) ?: return@withContext null
            val parsed = runCatching { javax.mail.internet.MimeMessage(null, msg.getInputStream()) }.getOrElse { msg }
            val part = findAttachmentPart(parsed, attachment) ?: return@withContext null
            val dir = java.io.File(UnifiedCommsApplication.getInstance().cacheDir, "attachments")
            dir.mkdirs()
            val safeName = attachment.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val file = java.io.File(dir, "${attachment.id}_$safeName")
            runCatching { (part as? javax.mail.internet.MimeBodyPart)?.saveFile(file.absolutePath) }.getOrNull()
                ?: file.writeBytes(part.inputStream.readBytes())
            return@withContext if (file.exists() && file.length() > 0) file.absolutePath else null
        } catch (e: Exception) {
            Log.e("EmailSyncEngineImpl", "fetchAttachment failed folder=$folder uid=$uid name=${attachment.fileName}: ${e.message}")
            null
        } finally {
            runCatching { imapFolder?.close(false) }
            runCatching { store?.close() }
        }
    }

    override suspend fun listFolders(account: Account): List<String> = withContext(Dispatchers.IO) {
        var store: javax.mail.Store? = null
        try {
            val config = account.serverConfig
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val session = openImapSession(config)
            store = session.getStore("imap") as com.sun.mail.imap.IMAPStore
            connectStoreWithRetry(store, config, auth)
            // ponytail: list EVERY real mail folder. The old chat logic moved mail
            // into a hidden "Chat" folder and this method excluded it; now that chat
            // is gone we surface all of them so nothing stays orphaned/invisible.
            val all = store.defaultFolder.list("*").filter { it.exists() && it.type and javax.mail.Folder.HOLDS_MESSAGES != 0 }
            val names = all.map { it.fullName }
            // INBOX first, then alphabetical — Edison-style ordering.
            names.sortedWith(compareBy({ it.lowercase() != "inbox" }, { it.lowercase() }))
        } catch (e: Exception) {
            Log.e("EmailSyncEngineImpl", "listFolders failed: ${e.message}")
            emptyList()
        } finally {
            runCatching { store?.close() }
        }
    }

    private fun findAttachmentPart(part: javax.mail.Part, target: com.unifiedcomms.data.model.Attachment): javax.mail.Part? {
        return try {
            val ct = runCatching { part.contentType }.getOrNull().orEmpty().lowercase()
            if (part is javax.mail.internet.MimeMultipart || ct.startsWith("multipart/")) {
                // ponytail: same root cause as extractAttachments — mislabeled
                // multipart/alternative drops the sibling PDF when trusting the
                // header. Use the boundary-aware parser (multipart/mixed) so the
                // on-demand fetch finds the real attachment part.
                val mp = boundaryAwareMultipart(part.inputStream)
                    ?: (part as? javax.mail.internet.MimeMultipart)
                if (mp != null) {
                    for (i in 0 until mp.count) {
                        findAttachmentPart(mp.getBodyPart(i) as javax.mail.Part, target)?.let { return it }
                    }
                }
                null
            } else if (ct.startsWith("message/rfc822")) {
                // ponytail: forwarded/embedded message can wrap the target attachment.
                val nested = runCatching { part.content as? javax.mail.internet.MimeMessage }.getOrNull()
                    ?: runCatching { javax.mail.internet.MimeMessage(null, part.inputStream) }.getOrNull()
                nested?.let { findAttachmentPart(it, target) } ?: null
            } else {
                val fileName = runCatching { part.fileName }.getOrNull()
                val cid = runCatching {
                    (part as? javax.mail.internet.MimeBodyPart)?.contentID
                }.getOrNull()?.trim('<', '>')
                val matches = (fileName != null && fileName == target.fileName) ||
                    (target.contentId != null && !target.contentId.isBlank() && cid == target.contentId.trim('<', '>')) ||
                    (fileName != null && target.fileName != null && fileName.equals(target.fileName, true))
                if (matches) part else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun smtpSession(
        config: com.unifiedcomms.data.model.ServerConfig,
        auth: com.unifiedcomms.data.model.AuthConfig
    ): Session {
        ensureMailMimeHandlers()
        val props = Properties().apply {
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", config.smtpPort)
            put("mail.smtp.auth", true)
            put("mail.smtp.starttls.enable", config.smtpUseStartTls)
            put("mail.smtp.connectiontimeout", 30000)
            put("mail.smtp.timeout", 30000)
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return if (auth.type == com.unifiedcomms.data.model.AuthType.OAUTH2) {
                    PasswordAuthentication(auth.username!!, buildXoauth2(auth.username!!, auth.oauthAccessToken.orEmpty()))
                } else {
                    PasswordAuthentication(auth.username!!, auth.passwordEncrypted!!)
                }
            }
        })
    }

    private fun calendarInviteMessage(
        session: Session,
        sender: String,
        recipients: List<String>,
        event: com.unifiedcomms.data.model.CalendarEvent
    ): MimeMessage {
        val multipart = MimeMultipart("mixed")
        multipart.addBodyPart(MimeBodyPart().apply {
            setContent(
                "<p>You are invited to ${event.title}.</p><p>Accept or decline from the UnifiedComms calendar invitation.</p>",
                "text/html; charset=utf-8"
            )
        })
        multipart.addBodyPart(MimeBodyPart().apply {
            setContent(
                VEventSerializer.toInvite(event),
                "text/calendar; method=REQUEST; charset=UTF-8"
            )
            setHeader("Content-Class", "urn:content-classes:calendarmessage")
        })
        return MimeMessage(session).apply {
            setFrom(InternetAddress(sender))
            recipients.forEach { addRecipient(RecipientType.TO, InternetAddress(it)) }
            subject = "Invitation: ${event.title.ifBlank { "Calendar event" }}"
            setHeader("X-Sogo-Message-Type", "calendar:invitation")
            setHeader("Content-Class", "urn:content-classes:calendarmessage")
            setContent(multipart)
        }
    }

    /** Send an RFC 5546 calendar invitation to the event's RSVP attendees. */
    suspend fun sendCalendarInvite(account: Account, event: com.unifiedcomms.data.model.CalendarEvent): SendResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = account.serverConfig
                if (config.smtpHost.isNullOrBlank()) {
                    return@withContext SendResult.failure("SMTP is not configured for this account")
                }
                val recipients = event.attendees
                    .map { it.email.trim() }
                    .filter { it.isNotBlank() && it.contains('@') && !it.equals(account.email, ignoreCase = true) }
                    .distinctBy { it.lowercase() }
                if (recipients.isEmpty()) {
                    return@withContext SendResult.failure("Event has no invite recipients")
                }
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val session = smtpSession(config, auth)
                val message = calendarInviteMessage(
                    session = session,
                    sender = account.email,
                    recipients = recipients,
                    event = event
                )
                Transport.send(message)
                SendResult.success(message.getHeader("Message-ID")?.firstOrNull() ?: java.util.UUID.randomUUID().toString())
            } catch (e: Exception) {
                Log.w("EmailSyncEngineImpl", "calendar invite send failed", e)
                SendResult.failure(e.message ?: "Calendar invite send failed")
            }
        }
    }

    override suspend fun sendEmail(account: Account, email: Email): SendResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = account.serverConfig
                if (config.smtpHost.isNullOrBlank()) {
                    return@withContext SendResult.failure("SMTP is not configured for this account")
                }
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val session = smtpSession(config, auth)

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

    override suspend fun setFlags(
        account: Account,
        folder: String,
        uid: String,
        flags: com.unifiedcomms.data.model.EmailFlags
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            var store: Store? = null
            var imapFolder: Folder? = null
            try {
                val numericUid = uid.toLongOrNull() ?: return@withContext SyncResult.failure("Invalid IMAP UID")
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)
                store = openImapSession(config).store
                connectStoreWithRetry(store!!, config, auth)
                imapFolder = store!!.getFolder(folder)
                if (!imapFolder!!.exists()) return@withContext SyncResult.failure("Folder not found: $folder")
                imapFolder!!.open(Folder.READ_WRITE)
                val message = (imapFolder as javax.mail.UIDFolder).getMessageByUID(numericUid)
                    ?: return@withContext SyncResult.failure("Message not found")
                message.setFlag(Flags.Flag.SEEN, flags.isRead)
                message.setFlag(Flags.Flag.FLAGGED, flags.isFlagged)
                message.setFlag(Flags.Flag.ANSWERED, flags.isAnswered)
                SyncResult.success()
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Flag update failed")
            } finally {
                runCatching { imapFolder?.close(false) }
                runCatching { store?.close() }
            }
        }
    }

    override suspend fun moveToFolder(account: Account, uids: List<String>, fromFolder: String, toFolder: String): SyncResult {
        return withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext SyncResult.failure("No messages selected")
            val numericUids = uids.map { it.toLongOrNull() ?: return@withContext SyncResult.failure("Invalid IMAP UID") }
            var store: Store? = null
            var src: Folder? = null
            var dst: Folder? = null
            try {
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val activeStore = openImapSession(config).store
                store = activeStore
                connectStoreWithRetry(activeStore, config, auth)
                val source = activeStore.getFolder(fromFolder)
                val destination = activeStore.getFolder(toFolder)
                src = source
                dst = destination
                if (!source.exists() || !destination.exists()) {
                    return@withContext SyncResult.failure("Folder not found: $fromFolder -> $toFolder")
                }
                source.open(Folder.READ_WRITE)
                val uidFolder = source as? javax.mail.UIDFolder
                    ?: return@withContext SyncResult.failure("IMAP UID folder is unavailable")
                val msgs = mutableListOf<JMailMessage>()
                numericUids.forEach { uid ->
                    val message = uidFolder.getMessageByUID(uid)
                        ?: return@withContext SyncResult.failure("Message not found: $uid")
                    msgs += message
                }
                destination.appendMessages(msgs.toTypedArray())
                msgs.forEach { it.setFlag(Flags.Flag.DELETED, true) }
                source.expunge()
                SyncResult.success(itemsSynced = msgs.size)
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Move failed")
            } finally {
                runCatching { src?.close(false) }
                runCatching { dst?.close(false) }
                runCatching { store?.close() }
            }
        }
    }

    override suspend fun deleteMessages(account: Account, folder: String, uids: List<String>): SyncResult {
        return withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext SyncResult.failure("No messages selected")
            val numericUids = uids.map { it.toLongOrNull() ?: return@withContext SyncResult.failure("Invalid IMAP UID") }
            var store: Store? = null
            var target: Folder? = null
            try {
                val config = account.serverConfig
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val activeStore = openImapSession(config).store
                store = activeStore
                connectStoreWithRetry(activeStore, config, auth)
                val targetFolder = activeStore.getFolder(folder)
                target = targetFolder
                if (!targetFolder.exists()) return@withContext SyncResult.failure("Folder not found: $folder")
                targetFolder.open(Folder.READ_WRITE)
                val uidFolder = targetFolder as? javax.mail.UIDFolder
                    ?: return@withContext SyncResult.failure("IMAP UID folder is unavailable")
                val msgs = mutableListOf<JMailMessage>()
                numericUids.forEach { uid ->
                    val message = uidFolder.getMessageByUID(uid)
                        ?: return@withContext SyncResult.failure("Message not found: $uid")
                    msgs += message
                }
                msgs.forEach { it.setFlag(Flags.Flag.DELETED, true) }
                targetFolder.expunge()
                uids.forEach { uid -> emailRepo.getByImapUid(account.id, uid, folder)?.let { emailRepo.deleteById(it.id) } }
                SyncResult.success(itemsSynced = msgs.size)
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Delete failed")
            } finally {
                runCatching { target?.close(false) }
                runCatching { store?.close() }
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
                // A connection TEST must not inherit the 5-minute bulk-sync read
                // timeout — a hanging IMAP connect would wedge the pre-save gate.
                // timedConnect hard-bounds the blocking connect at 15s regardless of
                // coroutine cancellation (which cannot interrupt OS-level TCP).
                val session = openImapSession(config)
                val store = session.getStore("imap")
                timedConnect(store, config.imapHost, null, auth.username!!, auth.passwordEncrypted!!, 15_000L)
                store.close()
                ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("IMAP"))
            } catch (e: Exception) {
                ConnectionTestResult(false, 0, emptyList(), classifyImapError(e))
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
