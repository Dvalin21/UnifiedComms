package com.unifiedcomms.data.e2ee

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.MessageType
import com.unifiedcomms.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * ChatSyncManager — polls relay inbox + maintains WebSocket push.
 *
 * Wires ChatCryptoManager + ChatRelayManager to the local MessageDao.
 * Uses ChatKeyManager for ECDH key agreement (Signal-lite).
 */
class ChatSyncManager(
    private val context: android.content.Context,
    private val relay: ChatRelayManager,
    private val crypto: ChatCryptoManager,
    private val keys: ChatKeyManager,
    private val messageDao: com.unifiedcomms.data.db.dao.MessageDao,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pollJob: Job? = null
    private var ws: WebSocket? = null
    private var wsPingJob: Job? = null
    private var lastInboxTs: Long = PreferencesManager.getInstance().getLong("chat_last_inbox_ts", 0L)

    private val currentUserId: String
        get() = PreferencesManager.getInstance().getString("current_user_id", "")

    private fun isValidPhone(phone: String): Boolean =
        phone.startsWith("+") && phone.drop(1).all { it.isDigit() } && phone.length in 8..16

    companion object {
        private const val TAG = "ChatSyncMgr"
        private const val WS_TAG = "ChatWS"
    }

    private fun conversationIdFor(a: String, b: String): String = listOf(a, b).sorted().joinToString(":")

    // ── Lazy registration ─────────────────────────────────────────────

    /** Register with the relay if not already registered. Called lazily on first send/poll. */
    private suspend fun ensureRegistered(): Result<Unit> {
        if (relay.bearerToken.isNotBlank()) return Result.success(Unit)
        val phone = currentUserId
        if (!isValidPhone(phone)) {
            Log.w(TAG, "Cannot register: no valid chat identity set (got '$phone'). Set it in Settings first.")
            return Result.failure(IllegalStateException("No valid chat identity set"))
        }
        val identityPub = keys.ensureIdentity()
        val preKeyPub = keys.ensurePreKey()
        val preKeyPubDer = android.util.Base64.decode(preKeyPub, android.util.Base64.NO_WRAP)
        val preKeySig = keys.signPreKey(preKeyPubDer)
        val req = ChatRelayManager.RegisterRequest(
            phone = phone,
            identity_pub = identityPub,
            identity_sig = preKeySig, // prekey signed by identity key (Signal convention)
            signed_prekey_pub = preKeyPub,
            signed_prekey_sig = "",
            one_time_prekey = "",
        )
        return relay.register(req).map { _bearerToken ->
            Log.d(TAG, "Registered with relay as $phone")
        }
    }

    /** Start periodic polling + WebSocket connection. */
    fun start(pollIntervalMs: Long = 30_000L) {
        stop()
        pollJob = scope.launch {
            while (isActive) {
                pollInboxAsync()
                delay(pollIntervalMs)
            }
        }
        connectWebSocket()
        // Trigger first poll immediately.
        pollInboxAsync()
    }

    /** Stop polling and WebSocket. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        wsPingJob?.cancel()
        wsPingJob = null
        ws?.close(1000, "stop")
        ws = null
    }

    /** Is the sync manager running? */
    fun isRunning(): Boolean = pollJob?.isActive == true

    // ── Inbox polling ────────────────────────────────────────────────

    private suspend fun pollInbox() {
        // Blocking registration: wait until we have a token before polling.
        if (relay.bearerToken.isBlank()) {
            ensureRegistered()
            return
        }

        relay.pollInbox(since = lastInboxTs, markRead = true).onSuccess { response ->
            if (response.messages.isNotEmpty()) {
                lastInboxTs = response.messages.maxOfOrNull { it.ts } ?: lastInboxTs
                PreferencesManager.getInstance().putLong("chat_last_inbox_ts", lastInboxTs)
                processIncomingMessages(response.messages)
            }
        }
    }

    private fun pollInboxAsync() {
        scope.launch { pollInbox() }
    }

    private suspend fun processIncomingMessages(messages: List<ChatRelayManager.InboxMessage>) {
        for (msg in messages) {
            val plaintext = decryptIncomingMessage(msg)
            if (plaintext == null) {
                Log.w(TAG, "Skipping unreadable message ${msg.msg_id} from ${msg.sender}")
                continue
            }

            val senderPhone = msg.sender
            val recipientPhone = currentUserId

            val message = Message(
                id = msg.msg_id,
                conversationId = conversationIdFor(senderPhone, recipientPhone),
                senderId = senderPhone,
                recipientId = recipientPhone,
                content = plaintext,
                messageType = MessageType.TEXT,
                status = MessageStatus.DELIVERED,
                isEncrypted = true,
                sentAt = kotlinx.datetime.Instant.fromEpochMilliseconds(msg.ts * 1000),
                deliveredAt = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                readAt = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                isLocalOnly = false,
                needsSync = false,
            )
            messageDao.insert(message)
            Log.d(TAG, "Stored incoming message from $senderPhone")
        }
    }

    // ── WebSocket ────────────────────────────────────────────────────

    private fun connectWebSocket() {
        if (relay.bearerToken.isBlank()) {
            android.util.Log.v(WS_TAG, "connectWebSocket: no bearer token, skipping")
            return
        }
        val wsUrl = relay.relayBaseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + "/ws"
        android.util.Log.v(WS_TAG, "connectWebSocket: url=$wsUrl")

        ws = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    android.util.Log.v(WS_TAG, "WebSocket opened")
                    webSocket.send("""{"token":"${relay.bearerToken}"}""")
                    wsPingJob = scope.launch {
                        while (isActive) {
                            delay(30_000)
                            webSocket.send("""{"type":"ping"}""")
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("\"type\":\"new_message\"")) {
                        Log.d(TAG, "WS push: new_message — polling inbox")
                        pollInboxAsync()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "WS failure: ${t.message}")
                    wsPingJob?.cancel()
                    wsPingJob = null
                    scope.launch {
                        delay(5_000)
                        if (relay.bearerToken.isNotBlank()) connectWebSocket()
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WS closed: $code $reason")
                    wsPingJob?.cancel()
                    wsPingJob = null
                }
            }
        )
    }

    // ── Sending ──────────────────────────────────────────────────────

    /**
     * Send an encrypted message to a peer.
     * 1. Fetch recipient's prekey from relay
     * 2. Generate ephemeral ECDH keypair
     * 3. ECDH(ephemeral_priv, recipient_prekey_pub) → HKDF → AES key
     * 4. Encrypt plaintext with AES-256-GCM
     * 5. POST envelope with ephemeral_pub
     * 6. Store locally
     *
     * Falls back to PSK-based encrypt if recipient has no prekey registered
     * (for backward compat with pre-v1 devices).
     */
    suspend fun sendMessage(
        peerPhone: String,
        plaintext: String,
        chainIndex: Long,
    ): Result<String> {
        // Lazy registration: if no token yet, register now before sending.
        if (relay.bearerToken.isBlank()) {
            val reg = ensureRegistered()
            if (reg.isFailure) return reg.map { "" }
        }

        // Look up recipient's prekey from relay
        val identityResult = relay.getIdentity(peerPhone)
        val preKeyBase64 = identityResult
            .map { it.signed_prekey_pub }
            .getOrNull()

        // Build envelope — encrypt using ECDH if we have the recipient's prekey,
        // otherwise fall back to PSK (old devices, no relay key exchange yet).
        val (ciphertext, ephemeralPub) = when {
            preKeyBase64 != null -> {
                val ephemeral = keys.generateEphemeralKeyPair()
                val sharedSecret = keys.ecdh(ephemeral.privateKey, preKeyBase64)
                val salt = Base64.decode(ephemeral.publicKeyBase64, Base64.NO_WRAP)
                val aesKey = keys.hkdfSha256(sharedSecret, salt)
                val ct = crypto.encryptWithKey(plaintext, aesKey)
                Pair(ct, ephemeral.publicKeyBase64)
            }
            else -> {
                Log.w(TAG, "Recipient $peerPhone has no prekey — falling back to PSK")
                Pair(crypto.encrypt(plaintext), "local")
            }
        }

        val envelope = ChatRelayManager.SendEnvelope(
            version = 1,
            from_number = currentUserId,
            to = listOf(peerPhone),
            ts = System.currentTimeMillis() / 1000,
            ciphertext = ciphertext,
            ephemeral_pub = ephemeralPub,
            chain_index = chainIndex,
        )

        return relay.send(envelope).map { ack ->
            val message = Message(
                id = ack.msg_id,
                conversationId = conversationIdFor(currentUserId, peerPhone),
                senderId = currentUserId,
                recipientId = peerPhone,
                content = plaintext,
                messageType = MessageType.TEXT,
                status = MessageStatus.SENT,
                isEncrypted = true,
                sentAt = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                isLocalOnly = false,
                needsSync = false,
            )
            messageDao.insert(message)
            Log.d(TAG, "Sent message to $peerPhone, msg_id=${ack.msg_id}")
            ack.msg_id
        }.onFailure { e ->
            Log.w(TAG, "Send failed", e)
            val message = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationIdFor(currentUserId, peerPhone),
                senderId = currentUserId,
                recipientId = peerPhone,
                content = plaintext,
                messageType = MessageType.TEXT,
                status = MessageStatus.PENDING,
                isEncrypted = true,
                sentAt = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                isLocalOnly = false,
                needsSync = true,
            )
            messageDao.insert(message)
        }
    }

    // ── E2EE receive (Signal-lite: ECDH with sender ephemeral) ──────

    /**
     * Decrypt an incoming message using the sender's ephemeral public key.
     * 1. ECDH(local_prekey_priv, sender_ephemeral_pub) → HKDF → AES key
     * 2. Decrypt payload
     */
    private suspend fun decryptIncomingMessage(msg: ChatRelayManager.InboxMessage): String? {
        val ephemeralPubBase64 = msg.ephemeral_pub
            ?: return null
        val senderPreKeyPriv = keys.getPreKeyPrivateKey() // this device's prekey
        val sharedSecret = keys.ecdh(senderPreKeyPriv, ephemeralPubBase64)
        val saltBytes = Base64.decode(ephemeralPubBase64, Base64.NO_WRAP)
        val aesKey = keys.hkdfSha256(sharedSecret, saltBytes)
        return try {
            crypto.decryptWithKey(msg.ciphertext, aesKey)
        } catch (e: Exception) {
            Log.w(TAG, "Decrypt failed for msg ${msg.msg_id}", e)
            null
        }
    }
}
