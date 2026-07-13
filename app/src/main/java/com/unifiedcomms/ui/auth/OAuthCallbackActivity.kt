package com.unifiedcomms.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.unifiedcomms.R
import com.unifiedcomms.data.model.AccountType

class OAuthCallbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri != null && uri.getQueryParameter("code") != null) {
            // The OAuth provider redirected here (this activity is the redirect
            // target). Forward the code to AddAccountActivity, which already knows
            // how to exchange it. Pass the accountType so a freshly-created instance
            // (process was killed while the browser was open) can still dispatch.
            val type = accountTypeFromRedirect(uri)
            val forward = Intent(this, AddAccountActivity::class.java).apply {
                data = uri
                type?.let { putExtra("accountType", it.name) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(forward)
        }
        finish()
    }

    // ponytail: the redirect scheme is unifiedcomms://oauth2redirect/<provider>;
    // the last path segment is the provider key.
    private fun accountTypeFromRedirect(uri: Uri): AccountType? = when (uri.lastPathSegment?.lowercase()) {
        "google" -> AccountType.GOOGLE
        "outlook" -> AccountType.OUTLOOK
        "yahoo" -> AccountType.YAHOO
        "icloud" -> AccountType.ICLOUD
        else -> null
    }
}