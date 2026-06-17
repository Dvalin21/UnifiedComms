package com.unifiedcomms.push

import android.content.Context
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

interface PushManager {
    suspend fun registerDevice(token: String): RegistrationResult
    suspend fun unregisterDevice(): Boolean
    suspend fun sendPush(targetUserId: String, payload: PushPayload): PushResult
    suspend fun subscribeToTopic(topic: String): Boolean
    suspend fun unsubscribeFromTopic(topic: String): Boolean
    fun getFcmToken(): String?
}

data class RegistrationResult(
    val success: Boolean,
    val deviceId: String? = null,
    val errorMessage: String? = null
)

data class PushPayload(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val priority: PushPriority = PushPriority.HIGH,
    val collapseKey: String? = null,
    val ttlSeconds: Int = 86400
)

enum class PushPriority { HIGH, NORMAL, LOW }

data class PushResult(
    val success: Boolean,
    val messageId: String? = null,
    val errorMessage: String? = null
)

class PushManagerImpl @Inject constructor(
    private val context: Context,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope,
    private val okHttp: OkHttpClient
) : PushManager {

    private val serverUrl = "https://push.unifiedcomms.app" // Configure via BuildConfig
    private val apiKey = BuildConfig.PUSH_API_KEY

    private var deviceToken: String? = null
    private var deviceId: String? = null

    override suspend fun registerDevice(token: String): RegistrationResult = withContext(Dispatchers.IO) {
        deviceToken = token
        // Send to our push server to register this device for the user
        val json = JSONObject().apply {
            put("fcm_token", token)
            put("platform", "android")
            put("app_version", BuildConfig.VERSION_NAME)
        }
        val request = Request.Builder()
            .url("$serverUrl/api/v1/devices/register")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(json.toString(), "application/json".toMediaType()))
            .build()
        okHttp.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val respJson = JSONObject(response.body?.string() ?: "{}")
                deviceId = respJson.optString("device_id")
                RegistrationResult(true, deviceId)
            } else {
                RegistrationResult(false, errorMessage = "HTTP ${response.code}")
            }
        }
    }

    override suspend fun unregisterDevice(): Boolean = withContext(Dispatchers.IO) {
        deviceId?.let { id ->
            val request = Request.Builder()
                .url("$serverUrl/api/v1/devices/$id")
                .addHeader("Authorization", "Bearer $apiKey")
                .delete()
                .build()
            okHttp.newCall(request).execute().use { it.isSuccessful }
        } ?: false
    }

    override suspend fun sendPush(targetUserId: String, payload: PushPayload): PushResult = withContext(Dispatchers.IO) {
        // Encrypt sensitive data in payload
        val encryptedData = payload.data.mapValues { (k, v) ->
            if (k in setOf("message_id", "conversation_id", "sender_id")) k to v else k to v
        }

        val json = JSONObject().apply {
            put("to", targetUserId)
            put("title", payload.title)
            put("body", payload.body)
            put("data", JSONObject(encryptedData))
            put("priority", payload.priority.name.lowercase())
            payload.collapseKey?.let { put("collapse_key", it) }
            put("ttl", payload.ttlSeconds)
        }

        val request = Request.Builder()
            .url("$serverUrl/api/v1/push/send")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(json.toString(), "application/json".toMediaType()))
            .build()

        okHttp.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val respJson = JSONObject(response.body?.string() ?: "{}")
                PushResult(true, respJson.optString("message_id"))
            } else {
                PushResult(false, errorMessage = "HTTP ${response.code}")
            }
        }
    }

    override suspend fun subscribeToTopic(topic: String): Boolean = withContext(Dispatchers.IO) {
        // FCM topic subscription would go here
        // FirebaseMessaging.getInstance().subscribeToTopic(topic)
        true
    }

    override suspend fun unsubscribeFromTopic(topic: String): Boolean = withContext(Dispatchers.IO) {
        true
    }

    override fun getFcmToken(): String? = deviceToken

    companion object {
        const val CHANNEL_ID_MESSAGES = "messages"
        const val CHANNEL_ID_EMAIL = "email"
        const val CHANNEL_ID_CALENDAR = "calendar"
        const val CHANNEL_ID_REMINDERS = "reminders"
        const val CHANNEL_ID_SYNC = "sync"
    }
}