package com.unifiedcomms.sync.accounts

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.unifiedcomms.ui.auth.AddAccountActivity

class UnifiedCommsAuthenticator(private val context: Context) : AbstractAccountAuthenticator(context) {

    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String
    ): Bundle {
        return Bundle()
    }

    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String,
        requiredFeatures: Array<out String>,
        options: Bundle
    ): Bundle {
        val intent = Intent(context, AddAccountActivity::class.java)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        intent.putExtra("accountType", accountType)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle
    ): Bundle {
        val intent = Intent(context, AddAccountActivity::class.java)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        intent.putExtra("account", account)
        intent.putExtra("confirmCredentials", true)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle
    ): Bundle {
        // Return existing token or trigger OAuth flow
        val accountManager = AccountManager.get(context)
        val authToken = accountManager.peekAuthToken(account, authTokenType)
        
        if (authToken != null) {
            val bundle = Bundle()
            bundle.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
            bundle.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
            bundle.putString(AccountManager.KEY_AUTHTOKEN, authToken)
            return bundle
        }

        // Need to re-authenticate
        val intent = Intent(context, AddAccountActivity::class.java)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        intent.putExtra("account", account)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    override fun getAuthTokenLabel(authTokenType: String): String {
        return when (authTokenType) {
            "oauth2" -> "OAuth 2.0"
            "password" -> "Password"
            else -> authTokenType
        }
    }

    override fun updateCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle
    ): Bundle {
        val intent = Intent(context, AddAccountActivity::class.java)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        intent.putExtra("account", account)
        intent.putExtra("updateCredentials", true)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<out String>
    ): Bundle {
        val bundle = Bundle()
        bundle.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false)
        return bundle
    }
}

class UnifiedCommsAuthenticatorProvider : android.content.ContentProvider() {
    private var authenticator: UnifiedCommsAuthenticator? = null

    override fun onCreate(): Boolean {
        authenticator = UnifiedCommsAuthenticator(context!!)
        return true
    }

    override fun query(uri: android.net.Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor? = null

    override fun getType(uri: android.net.Uri): String? = null

    override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null

    override fun delete(uri: android.net.Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}