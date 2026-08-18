package com.unifiedcomms.data.e2ee

import android.content.Context
import android.util.Base64
import android.util.Log
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.MessageType
import com.unifiedcomms.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 */
class ChatSyncManager(
    private val context: Context,
    private val relay: ChatRelayManager,
    private val crypto: ChatCryptoManager,
    private val messageDao: com.unifiedcomms.data.db.dao.MessageDao,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)

    private var pollJob: Job? = null
    private var ws: WebSocket? = null
    private var wsPingJob: Job? = null
    private var lastInboxTs: Long = PreferencesManager.getInstance().getLong("chat_last_inbox_ts", 0L)

    private val currentUserId: String
        get() = PreferencesManager.getInstance().getString("current_user_id", "current_user")

    companion object {
        private const val TAG = "ChatSyncManager"
    }

    /** Start periodic polling + WebSocket connection. */
    fun start(pollIntervalMs: Long = 30_000L) {
        stop()
        pollJob = scope.launch {
            while (isActive) {
                pollInbox()
                delay(pollIntervalMs)
            }
        }
        connectWebSocket()
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

    private fun pollInbox() {
        if (relay.bearerToken.isBlank()) return

        scope.launch {
            relay.pollInbox(since = lastInboxTs, markRead = true).onSuccess { response ->
                if (response.messages.isNotEmpty()) {
                    lastInboxTs = response.messages.maxOfOrNull { it.ts } ?: lastInboxTs
                    PreferencesManager.getInstance().putLong("chat_last_inbox_ts", lastInboxTs)
                    processIncomingMessages(response.messages)
                }
            }
        }
    }

    private suspend fun processIncomingMessages(messages: List<ChatRelayManager.InboxMessage>) {
        for (msg in messages) {
            try {
                val plaintext = crypto.decrypt(msg.ciphertext)
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
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process message ${msg.msg_id}", e)
            }
        }
    }

    // ── WebSocket ────────────────────────────────────────────────────

    private fun connectWebSocket() {
        if (relay.bearerToken.isBlank()) return

        val wsUrl = relay.relayBaseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + "/ws"

        ws = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WS connected")
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
                        pollInbox()
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
     * 1. Encrypt plaintext
     * 2. POST to relay
     * 3. Store locally
     */
    suspend fun sendMessage(
        peerPhone: String,
        plaintext: String,
        chainIndex: Long,
    ): Result<String> {
        if (relay.bearerToken.isBlank()) {
            return Result.failure(Exception("Not registered with relay"))
        }

        val ciphertext = crypto.encrypt(plaintext)
        val envelope = ChatRelayManager.SendEnvelope(
            version = 1,
            from_number = currentUserId,
            to = listOf(peerPhone),
            ts = System.currentTimeMillis() / 1000,
            ciphertext = ciphertext,
            ephemeral_pub = "local",
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

    /** Generate a deterministic conversation ID for two phones. */
    private fun conversationIdFor(phoneA: String, phoneB: String): String {
        val sorted = listOf(phoneA, phoneB).sorted()
        return "conv:${Base64.encodeToString(sorted.joinToString("|").toByteArray(), Base64.NO_WRAP)}"
    }
}
