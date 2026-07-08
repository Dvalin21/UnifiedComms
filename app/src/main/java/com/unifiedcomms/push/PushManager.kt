package com.unifiedcomms.push

import android.content.Context
import android.content.pm.PackageManager
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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

class PushManagerImpl(
    private val context: Context,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope,
    private val okHttp: OkHttpClient
) : PushManager {

    private val serverUrl = "https://push.unifiedcomms.app"
    private val prefName = "push_prefs"
    private val keyApiKey = "api_key"
    private var deviceToken: String? = null
    private var deviceId: String? = null

    private fun apiKey(): String? {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val raw = prefs.getString(keyApiKey, "") ?: ""
        return raw.takeIf { it.isNotBlank() }
    }

    private fun bearerAuthHeader(): String? = apiKey()?.let { "Bearer $it" }

    fun setApiKey(value: String?) {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        prefs.edit().putString(keyApiKey, value.orEmpty()).apply()
    }

    private fun getAppVersion(): String {
        return try {
            val raw = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            raw
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    override suspend fun registerDevice(token: String): RegistrationResult = withContext(Dispatchers.IO) {
        deviceToken = token
        val json = JSONObject().apply {
            put("fcm_token", token)
            put("platform", "android")
            put("app_version", getAppVersion())
        }
        val mediaType = "application/json".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$serverUrl/api/v1/devices/register")
            .apply { bearerAuthHeader()?.let { addHeader("Authorization", it) } }
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
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
                .apply { bearerAuthHeader()?.let { addHeader("Authorization", it) } }
                .delete()
                .build()
            okHttp.newCall(request).execute().use { it.isSuccessful }
        } ?: false
    }

    override suspend fun sendPush(targetUserId: String, payload: PushPayload): PushResult = withContext(Dispatchers.IO) {
        val encryptedData = payload.data.mapValues { (k, v) ->
            if (k in setOf("message_id", "conversation_id", "sender_id")) {
                val encrypted = crypto.encrypt(v.toByteArray(Charsets.UTF_8))
                k to android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
            } else k to v
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

        val mediaType = "application/json".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$serverUrl/api/v1/push/send")
            .apply { bearerAuthHeader()?.let { addHeader("Authorization", it) } }
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
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
        val id = deviceId ?: return@withContext false
        val request = Request.Builder()
            .url("$serverUrl/api/v1/devices/$id/subscribe")
            .apply { bearerAuthHeader()?.let { addHeader("Authorization", it) } }
            .addHeader("Content-Type", "application/json")
            .post("{\"topic\":\"$topic\"}".toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { okHttp.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    override suspend fun unsubscribeFromTopic(topic: String): Boolean = withContext(Dispatchers.IO) {
        val id = deviceId ?: return@withContext false
        val request = Request.Builder()
            .url("$serverUrl/api/v1/devices/$id/unsubscribe")
            .apply { bearerAuthHeader()?.let { addHeader("Authorization", it) } }
            .addHeader("Content-Type", "application/json")
            .post("{\"topic\":\"$topic\"}".toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { okHttp.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
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
