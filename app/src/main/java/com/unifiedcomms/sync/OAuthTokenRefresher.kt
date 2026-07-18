package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.AuthType
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Refreshes OAuth2 access tokens before a sync so accounts don't die at expiry.
 * ponytail: single chokepoint. Hit only when auth type is OAUTH2 and a refresh token exists.
 */
class OAuthTokenRefresher(
    private val accountRepo: AccountRepository,
    private val crypto: CryptoManager,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    /**
     * Returns an account whose authConfig carries a fresh access token.
     * On any failure, returns the original account unchanged so callers can still attempt
     * a normal (possibly-expired) connection and surface the real auth error.
     */
    suspend fun ensureFreshToken(account: Account): Account {
        val auth = crypto.decryptAuthConfig(account.authConfig)
        if (auth.type != AuthType.OAUTH2) return account
        val refreshToken = auth.oauthRefreshToken ?: return account
        // Only refresh if missing, or expiring within 5 minutes.
        val expiry = account.authConfig.oauthTokenExpiry
        val needsRefresh = auth.oauthAccessToken == null ||
                expiry != null && expiry.toEpochMilliseconds() < Clock.System.now().toEpochMilliseconds() + 5 * 60 * 1000
        if (!needsRefresh) return account

        val tokenUrl = account.serverConfig.oauthTokenUrl ?: return account
        return withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("client_id", account.serverConfig.oauthClientId ?: "")
                    .add("refresh_token", refreshToken)
                    .add("grant_type", "refresh_token")
                    .build()
                val req = Request.Builder().url(tokenUrl).post(body).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "token refresh failed for ${account.email}: HTTP ${resp.code}")
                        return@withContext account
                    }
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    val newAccess = json.optString("access_token").takeIf { it.isNotBlank() } ?: return@withContext account
                    val expiresIn = json.optLong("expires_in", 0L)
                    val newExpiry = if (expiresIn > 0) {
                        Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() + expiresIn * 1000)
                    } else {
                        expiry
                    }
                    val updatedAuth = auth.copy(
                        oauthAccessToken = newAccess,
                        oauthTokenExpiry = newExpiry
                    )
                    val updatedAccount = account.copy(authConfig = updatedAuth)
                    accountRepo.update(updatedAccount)
                    Log.i(TAG, "token refreshed for ${account.email}")
                    // ponytail: re-read from repo so callers get the re-encrypted
                    // AuthConfig (engines decryptAuthConfig on it). Returning the in-memory
                    // `updatedAccount` would hand engines a PLAINTEXT token and make GCM
                    // decrypt throw on every post-refresh sync.
                    accountRepo.getById(account.id) ?: updatedAccount
                }
            } catch (e: Exception) {
                Log.w(TAG, "token refresh error for ${account.email}", e)
                account
            }
        }
    }

    companion object {
        private const val TAG = "OAuthTokenRefresher"
    }
}
