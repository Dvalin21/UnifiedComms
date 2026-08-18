package com.unifiedcomms.data.e2ee

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * ChatRelayManager — OkHttp client for the UnifiedComms encrypted relay.
 *
 * Handles registration, identity lookup, message send, and inbox poll.
 */
class ChatRelayManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    var bearerToken: String = ""
        private set

    var relayBaseUrl: String = DEFAULT_RELAY_URL
        set(value) {
            field = value.trimEnd('/')
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_RELAY_URL, field).apply()
        }

    companion object {
        private const val TAG = "ChatRelayManager"
        private const val PREFS_NAME = "uc_chat_relay"
        private const val PREF_TOKEN = "bearer_token"
        private const val PREF_RELAY_URL = "relay_url"
        private const val DEFAULT_RELAY_URL = "http://10.0.2.2:8444"
    }

    fun loadSavedState() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        bearerToken = prefs.getString(PREF_TOKEN, "") ?: ""
        relayBaseUrl = prefs.getString(PREF_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL
    }

    fun saveToken() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_TOKEN, bearerToken).apply()
    }

    fun clearToken() {
        bearerToken = ""
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(PREF_TOKEN).apply()
    }

    // ── Registration ──────────────────────────────────────────────────

    data class RegisterRequest(
        val phone: String,
        val identity_pub: String,
        val identity_sig: String,
        val signed_prekey_pub: String,
        val signed_prekey_sig: String,
        val one_time_prekey: String,
        val device_id: String = "",
    )

    data class RegisterResponse(
        val phone: String,
        val token: String,
        val expires_ts: Long,
        val note: String? = null,
    )

    suspend fun register(request: RegisterRequest): Result<RegisterResponse> = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(request).toRequestBody(jsonMediaType)
            val response = client.newCall(
                Request.Builder()
                    .url("$relayBaseUrl/v1/register")
                    .post(body)
                    .build()
            ).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Register failed: ${response.code}")
                )
            }
            val resp = gson.fromJson(response.body?.string(), RegisterResponse::class.java)
            bearerToken = resp.token
            saveToken()
            Result.success(resp)
        } catch (e: Exception) {
            Log.w(TAG, "Register failed", e)
            Result.failure(e)
        }
    }

    // ── Identity lookup ──────────────────────────────────────────────

    data class IdentityResponse(
        val identity_pub: String,
        val signed_prekey_pub: String,
        val one_time_prekey: String? = null,
    )

    suspend fun getIdentity(phone: String): Result<IdentityResponse> = withContext(Dispatchers.IO) {
        try {
            val encoded = android.net.Uri.encode(phone)
            val response = client.newCall(
                Request.Builder()
                    .url("$relayBaseUrl/v1/identity/$encoded")
                    .addHeader("Authorization", "Bearer $bearerToken")
                    .get()
                    .build()
            ).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Identity lookup failed: HTTP ${response.code}"))
            }
            val raw = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(raw, IdentityResponse::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Identity lookup failed for $phone", e)
            Result.failure(e)
        }
    }

    // ── Send ──────────────────────────────────────────────────────────

    data class SendEnvelope(
        val version: Int = 1,
        val from_number: String,
        val to: List<String>,
        val ts: Long,
        val ciphertext: String,
        val ephemeral_pub: String,
        val chain_index: Long,
        val group_id: String? = null,
        val fallback_hint: String? = null,
    )

    data class SendAck(val msg_id: String, val queued: Int)

    suspend fun send(envelope: SendEnvelope): Result<SendAck> = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(envelope).toRequestBody(jsonMediaType)
            val response = client.newCall(
                Request.Builder()
                    .url("$relayBaseUrl/v1/send")
                    .addHeader("Authorization", "Bearer $bearerToken")
                    .post(body)
                    .build()
            ).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Send failed: HTTP ${response.code}"))
            }
            val ack = gson.fromJson(response.body?.string(), SendAck::class.java)
            Result.success(ack)
        } catch (e: Exception) {
            Log.w(TAG, "Send failed", e)
            Result.failure(e)
        }
    }

    // ── Inbox ─────────────────────────────────────────────────────────

    data class InboxMessage(
        val msg_id: String,
        val sender: String,
        val ts: Long,
        val ciphertext: String,
        val ephemeral_pub: String,
        val chain_index: Long,
        val group_id: String? = null,
        val fallback_hint: String? = null,
        val delivered_ts: Long? = null,
    )

    data class InboxResponse(val messages: List<InboxMessage>, val count: Int)

    suspend fun pollInbox(since: Long = 0, markRead: Boolean = false): Result<InboxResponse> = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("$relayBaseUrl/v1/inbox?since=$since")
                if (markRead) append("&mark_read=true")
            }
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $bearerToken")
                    .get()
                    .build()
            ).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Inbox poll failed: HTTP ${response.code}"))
            }
            val resp = gson.fromJson(response.body?.string(), InboxResponse::class.java)
            Result.success(resp)
        } catch (e: Exception) {
            Log.w(TAG, "Inbox poll failed", e)
            Result.failure(e)
        }
    }

    // ── Health check ──────────────────────────────────────────────────

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder()
                    .url("$relayBaseUrl/healthz")
                    .get()
                    .build()
            ).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
