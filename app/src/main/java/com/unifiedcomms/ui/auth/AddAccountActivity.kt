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
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.security.CryptoManager
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.data.db.dao.AccountDao
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddAccountActivity : AppCompatActivity() {

    private lateinit var accountRepo: AccountRepository
    private lateinit var crypto: CryptoManager

    private val scope = CoroutineScope(Dispatchers.Main)

    private var accountType: AccountType? = null
    private var pendingIntent: android.app.PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dependencies manually since Hilt is disabled
        val db = UnifiedCommsDatabase.getInstance(this)
        val accountDao = db.accountDao()
        accountRepo = AccountRepositoryImpl(accountDao)
        crypto = CryptoManagerImpl(this)

        val intent = intent
        accountType = AccountType.valueOf(intent.getStringExtra("accountType") ?: "")
        pendingIntent = intent.getParcelableExtra(android.accounts.AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)

        when (accountType) {
            AccountType.GOOGLE -> startGoogleAuth()
            AccountType.OUTLOOK -> startOutlookAuth()
            AccountType.YAHOO -> startYahooAuth()
            AccountType.ICLOUD -> startIcloudAuth()
            AccountType.EXCHANGE -> startExchangeAuth()
            AccountType.MAILCOW -> showManualSetup()
            else -> showManualSetup()
        }
    }

    private fun startGoogleAuth() {
        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
            "client_id=${BuildConfig.GOOGLE_CLIENT_ID}&" +
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
        val authUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?" +
            "client_id=${BuildConfig.MICROSOFT_CLIENT_ID}&" +
            "redirect_uri=${Uri.encode("unifiedcomms://oauth2redirect/outlook")}&" +
            "response_type=code&" +
            "scope=${Uri.encode("https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send https://outlook.office.com/Calendars.ReadWrite https://outlook.office.com/Contacts.ReadWrite https://outlook.office.com/Tasks.ReadWrite")}"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        startActivity(intent)
    }

    private fun startYahooAuth() {
        val authUrl = "https://api.login.yahoo.com/oauth2/request_auth?" +
            "client_id=${BuildConfig.YAHOO_CLIENT_ID}&" +
            "redirect_uri=${Uri.encode("unifiedcomms://oauth2redirect/yahoo")}&" +
            "response_type=code&" +
            "scope=mail-r%20mail-w%20cal-r%20cal-w%20contacts-r%20contacts-w"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        startActivity(intent)
    }

    private fun startIcloudAuth() {
        val authUrl = "https://appleid.apple.com/auth/authorize?" +
            "client_id=${BuildConfig.APPLE_CLIENT_ID}&" +
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
                val id = accountRepo.insert(account)
                accountRepo.setDefault(account.id)
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
            authConfig = AuthConfig.Password(email, password),
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

    private suspend fun exchangeGoogleCode(code: String) {
        // Exchange code for tokens via Google OAuth2 token endpoint
        // Then create account with authConfig = AuthConfig.OAuth2(accessToken, refreshToken)
        // For now, placeholder
        val account = Account(
            name = "Google Account",
            email = "user@gmail.com",
            accountType = AccountType.GOOGLE,
            serverConfig = com.unifiedcomms.data.model.ServerConfig.GoogleDefaults(),
            authConfig = AuthConfig.OAuth2("access_token", "refresh_token"),
            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
        )
        val id = accountRepo.insert(account)
        accountRepo.setDefault(account.id)
        finishWithResult(account)
    }

    private suspend fun exchangeOutlookCode(code: String) {
        // Placeholder
        val account = Account(
            name = "Outlook Account",
            email = "user@outlook.com",
            accountType = AccountType.OUTLOOK,
            serverConfig = com.unifiedcomms.data.model.ServerConfig.OutlookDefaults(),
            authConfig = AuthConfig.OAuth2("access_token", "refresh_token"),
            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
        )
        val id = accountRepo.insert(account)
        accountRepo.setDefault(account.id)
        finishWithResult(account)
    }

    private suspend fun exchangeYahooCode(code: String) {
        // Placeholder
        val account = Account(
            name = "Yahoo Account",
            email = "user@yahoo.com",
            accountType = AccountType.YAHOO,
            serverConfig = com.unifiedcomms.data.model.ServerConfig.YahooDefaults(),
            authConfig = AuthConfig.OAuth2("access_token", "refresh_token"),
            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
        )
        val id = accountRepo.insert(account)
        accountRepo.setDefault(account.id)
        finishWithResult(account)
    }

    private suspend fun exchangeIcloudCode(code: String) {
        // Placeholder
        val account = Account(
            name = "iCloud Account",
            email = "user@icloud.com",
            accountType = AccountType.ICLOUD,
            serverConfig = com.unifiedcomms.data.model.ServerConfig.ICantDefaults(),
            authConfig = AuthConfig.OAuth2("access_token", "refresh_token"),
            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
        )
        val id = accountRepo.insert(account)
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