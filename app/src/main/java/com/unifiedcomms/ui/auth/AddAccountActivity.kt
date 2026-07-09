package com.unifiedcomms.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.unifiedcomms.R
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.security.CryptoManager
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.data.db.dao.AccountDao
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AddAccountActivity : AppCompatActivity() {

    private lateinit var accountRepo: AccountRepository
    private lateinit var crypto: CryptoManager

    private val scope = CoroutineScope(Dispatchers.Main)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private var accountType: AccountType? = null
    private var pendingIntent: android.app.PendingIntent? = null

    private fun getBuildConfigField(fieldName: String): String {
        return try {
            Class.forName("com.unifiedcomms.BuildConfig").getField(fieldName).get(null) as String
        } catch (e: Exception) {
            ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dependencies manually since Hilt is disabled
        val db = UnifiedCommsDatabase.getInstance(this)
        val accountDao = db.accountDao()
        crypto = CryptoManagerImpl(this)
        accountRepo = AccountRepositoryImpl(accountDao, crypto)

        accountType = runCatching { AccountType.valueOf(intent?.getStringExtra("accountType")?.ifBlank { null } ?: return showManualSetup()) }.getOrNull()
            ?: AccountType.GENERIC_IMAP_SMTP
        @Suppress("DEPRECATION")
        pendingIntent = intent.getParcelableExtra(android.accounts.AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)

        when (accountType) {
            AccountType.GOOGLE -> startGoogleAuth()
            AccountType.OUTLOOK -> startOutlookAuth()
            AccountType.YAHOO -> startYahooAuth()
            AccountType.ICLOUD -> startIcloudAuth()
            AccountType.EXCHANGE -> showManualSetup()
            else -> showManualSetup()
        }
    }

    private fun startGoogleAuth() {
        val clientId = getBuildConfigField("GOOGLE_CLIENT_ID")
        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
            "client_id=$clientId&" +
            "redirect_uri=${Uri.encode("unifiedcomms://oauth2redirect/google")}&" +
            "response_type=code&" +
            "scope=${Uri.encode("https://mail.google.com/ https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/contacts https://www.googleapis.com/auth/tasks")}&" +
            "access_type=offline&" +
            "prompt=consent"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        startActivity(intent)
    }

    private fun startOutlookAuth() {
        val clientId = getBuildConfigField("MICROSOFT_CLIENT_ID")
        val authUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?" +
            "client_id=$clientId&" +
            "redirect_uri=${Uri.encode("unifiedcomms://oauth2redirect/outlook")}&" +
            "response_type=code&" +
            "scope=${Uri.encode("https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send https://outlook.office.com/Calendars.ReadWrite https://outlook.office.com/Contacts.ReadWrite https://outlook.office.com/Tasks.ReadWrite")}"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        startActivity(intent)
    }

    private fun startYahooAuth() {
        val clientId = getBuildConfigField("YAHOO_CLIENT_ID")
        val authUrl = "https://api.login.yahoo.com/oauth2/request_auth?" +
            "client_id=$clientId&" +
            "redirect_uri=${Uri.encode("unifiedcomms://oauth2redirect/yahoo")}&" +
            "response_type=code&" +
            "scope=mail-r%20mail-w%20cal-r%20cal-w%20contacts-r%20contacts-w"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        startActivity(intent)
    }

    private fun startIcloudAuth() {
        val clientId = getBuildConfigField("APPLE_CLIENT_ID")
        val authUrl = "https://appleid.apple.com/auth/authorize?" +
            "client_id=$clientId&" +
            "redirect_uri=${Uri.encode("unifiedcomms://oauth2redirect/icloud")}&" +
            "response_type=code&" +
            "scope=name%20email%20icloud.calendars%20icloud.contacts%20icloud.tasks&" +
            "response_mode=form_post"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        startActivity(intent)
    }

    private fun startExchangeAuth() {
        // Exchange uses the same OAuth as Outlook for Office 365
        // For on-premises Exchange, show manual setup
        showManualSetup()
    }

    private fun showManualSetup() {
        setContentView(R.layout.activity_add_account_manual)

        val serverInput = findViewById<android.widget.EditText>(R.id.server_input)
        val emailInput = findViewById<android.widget.EditText>(R.id.email_input)
        val passwordInput = findViewById<android.widget.EditText>(R.id.password_input)
        val nameInput = findViewById<android.widget.EditText>(R.id.name_input)
        val connectButton = findViewById<android.widget.Button>(R.id.connect_button)

        connectButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val server = serverInput.text.toString().trim()
            val name = nameInput.text.toString().trim()

            if (email.isBlank() || password.isBlank() || server.isBlank()) return@setOnClickListener

            scope.launch {
                val account = createManualAccount(accountType!!, email, password, server, name)
                accountRepo.insert(account)
                accountRepo.setDefault(account.id)

                // Trigger immediate full sync for the newly connected account.
                // Without this, the app shows empty folders and requires manual sync.
                runCatching {
                    val app = application as com.unifiedcomms.UnifiedCommsApplication
                    val vm = com.unifiedcomms.ui.main.MainViewModel(app)
                    vm.syncAccount(account)
                }

                finishWithResult(account)
            }
        }
    }

    private fun createManualAccount(
        type: AccountType,
        email: String,
        password: String,
        server: String,
        name: String
    ): Account {
        val serverConfig = when (type) {
            AccountType.MAILCOW -> com.unifiedcomms.data.model.ServerConfig.MailcowDefaults(server)
            AccountType.GENERIC_IMAP_SMTP -> com.unifiedcomms.data.model.ServerConfig(
                imapHost = server,
                smtpHost = server,
                caldavUrl = "$server/dav/",
                carddavUrl = "$server/dav/"
            )
            AccountType.GENERIC_CALDAV_CARDDAV -> com.unifiedcomms.data.model.ServerConfig(
                caldavUrl = server,
                carddavUrl = server
            )
            AccountType.EXCHANGE -> com.unifiedcomms.data.model.ServerConfig.ExchangeDefaults(server)
            else -> com.unifiedcomms.data.model.ServerConfig.MailcowDefaults(server)
        }

        return Account(
            name = name.ifBlank { email },
            email = email,
            accountType = type,
            serverConfig = serverConfig,
            authConfig = AuthConfig.AppPassword(email, password),
            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(intent: Intent) {
        val uri = intent.data ?: return
        val code = uri.getQueryParameter("code") ?: return

        scope.launch {
            when (accountType) {
                AccountType.GOOGLE -> exchangeGoogleCode(code)
                AccountType.OUTLOOK -> exchangeOutlookCode(code)
                AccountType.YAHOO -> exchangeYahooCode(code)
                AccountType.ICLOUD -> exchangeIcloudCode(code)
                else -> return@launch
            }
        }
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("token_type") val tokenType: String? = null,
        @SerialName("scope") val scope: String? = null,
        @SerialName("id_token") val idToken: String? = null
    )

    @Serializable
    private data class GoogleUserInfo(
        val email: String,
        val verified_email: Boolean = false,
        val name: String? = null,
        val picture: String? = null
    )

    @Serializable
    private data class MicrosoftUserInfo(
        val mail: String? = null,
        val userPrincipalName: String? = null,
        val displayName: String? = null
    )

    @Serializable
    private data class YahooUserInfo(
        val email: String,
        val given_name: String? = null,
        val family_name: String? = null
    )

    private suspend fun exchangeGoogleCode(code: String) {
        val clientId = getBuildConfigField("GOOGLE_CLIENT_ID")
        val tokenUrl = "https://oauth2.googleapis.com/token"
        val form = FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId)
            .add("redirect_uri", "unifiedcomms://oauth2redirect/google")
            .add("grant_type", "authorization_code")
            .build()
        val req = Request.Builder().url(tokenUrl).post(form).build()
        val tokenResp = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return
            json.decodeFromString(TokenResponse.serializer(), resp.body!!.string())
        }
        val userReq = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v2/userinfo")
            .addHeader("Authorization", "Bearer ${tokenResp.accessToken}")
            .build()
        val userInfo = http.newCall(userReq).execute().use { resp ->
            if (!resp.isSuccessful) return
            json.decodeFromString(GoogleUserInfo.serializer(), resp.body!!.string())
        }
        createOAuthAccount(AccountType.GOOGLE, userInfo.email, tokenResp, userInfo.name, ServerConfig.GoogleDefaults())
    }

    private suspend fun exchangeOutlookCode(code: String) {
        val clientId = getBuildConfigField("MICROSOFT_CLIENT_ID")
        val tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        val form = FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId)
            .add("redirect_uri", "unifiedcomms://oauth2redirect/outlook")
            .add("grant_type", "authorization_code")
            .build()
        val req = Request.Builder().url(tokenUrl).post(form).build()
        val tokenResp = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return
            json.decodeFromString(TokenResponse.serializer(), resp.body!!.string())
        }
        val userReq = Request.Builder()
            .url("https://graph.microsoft.com/oidc/userinfo")
            .addHeader("Authorization", "Bearer ${tokenResp.accessToken}")
            .build()
        val userInfo = http.newCall(userReq).execute().use { resp ->
            if (!resp.isSuccessful) return
            json.decodeFromString(MicrosoftUserInfo.serializer(), resp.body!!.string())
        }
        val email = userInfo.mail ?: userInfo.userPrincipalName ?: return
        createOAuthAccount(AccountType.OUTLOOK, email, tokenResp, userInfo.displayName, ServerConfig.OutlookDefaults())
    }

    private suspend fun exchangeYahooCode(code: String) {
        val clientId = getBuildConfigField("YAHOO_CLIENT_ID")
        val clientSecret = getBuildConfigField("YAHOO_CLIENT_SECRET")
        val tokenUrl = "https://api.login.yahoo.com/oauth2/get_token"
        val formBuilder = FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId)
            .add("redirect_uri", "unifiedcomms://oauth2redirect/yahoo")
            .add("grant_type", "authorization_code")
        if (!clientSecret.isNullOrBlank()) {
            formBuilder.add("client_secret", clientSecret)
        }
        val form = formBuilder.build()
        val req = Request.Builder().url(tokenUrl).post(form).build()
        val tokenResp = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return
            json.decodeFromString(TokenResponse.serializer(), resp.body!!.string())
        }
        val userReq = Request.Builder()
            .url("https://api.login.yahoo.com/openid/v1/userinfo")
            .addHeader("Authorization", "Bearer ${tokenResp.accessToken}")
            .build()
        val userInfo = http.newCall(userReq).execute().use { resp ->
            if (!resp.isSuccessful) return
            json.decodeFromString(YahooUserInfo.serializer(), resp.body!!.string())
        }
        createOAuthAccount(AccountType.YAHOO, userInfo.email, tokenResp, "${userInfo.given_name ?: ""} ${userInfo.family_name ?: ""}".trim(), ServerConfig.YahooDefaults())
    }

    private suspend fun exchangeIcloudCode(code: String) {
        val clientId = getBuildConfigField("APPLE_CLIENT_ID")
        val tokenUrl = "https://appleid.apple.com/auth/token"
        val form = FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId)
            .add("redirect_uri", "unifiedcomms://oauth2redirect/icloud")
            .add("grant_type", "authorization_code")
            .build()
        val req = Request.Builder().url(tokenUrl).post(form).build()
        val tokenResp = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return
            json.decodeFromString(TokenResponse.serializer(), resp.body!!.string())
        }
        val email = tokenResp.idToken?.substringBefore("@")?.plus("@icloud.com") ?: return
        createOAuthAccount(AccountType.ICLOUD, email, tokenResp, email.substringBefore("@"), ServerConfig.ICloudDefaults())
    }

    private suspend fun createOAuthAccount(
        type: AccountType,
        email: String,
        token: TokenResponse,
        displayName: String?,
        serverConfig: ServerConfig
    ) {
        val account = Account(
            name = displayName ?: email,
            email = email,
            accountType = type,
            serverConfig = serverConfig,
            authConfig = AuthConfig.OAuth2(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                // ponytail: without expiry the refresher never re-fires (its needsRefresh
                // guard requires a non-null expiry), so OAuth accounts died at token
                // expiry. Stamp it from the grant's expires_in.
                expiry = token.expiresIn?.let { seconds ->
                    kotlinx.datetime.Instant.fromEpochMilliseconds(
                        kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + seconds * 1000
                    )
                }
            ),
            syncConfig = SyncConfig.Defaults(),
            uiConfig = UIConfig.Defaults()
        )
        accountRepo.insert(account)
        accountRepo.setDefault(account.id)
        finishWithResult(account)
    }

    private fun finishWithResult(account: Account) {
        val result = Intent().apply {
            putExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME, account.email)
            putExtra(android.accounts.AccountManager.KEY_ACCOUNT_TYPE, "com.unifiedcomms.account")
        }
        setResult(Activity.RESULT_OK, result)

        pendingIntent?.let { pending ->
            try {
                pending.send(this, Activity.RESULT_OK, result)
            } catch (e: Exception) {
                Log.e("AddAccountActivity", "Failed to send result", e)
            }
        }

        finish()
    }
}
