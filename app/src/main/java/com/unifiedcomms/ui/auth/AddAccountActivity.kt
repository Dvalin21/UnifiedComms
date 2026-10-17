package com.unifiedcomms.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Legacy View-based add-account Activity.
 *
 * ponytail: the real implementation is the Compose AddAccountScreen in MainActivity.
 * This Activity is kept only because the OS AccountManager (Authenticator.addAccount)
 * launches it for system-initiated account adds. It now just forwards to MainActivity
 * so all account-add flows share one code path (provisionAccount gate, ProviderProfiles,
 * OAuth, etc.).
 */
class AddAccountActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, com.unifiedcomms.ui.main.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Forward the OAuth redirect URI too — without it MainActivity/AddAccountScreen
            // never sees the authorization code and OAuth can never complete.
            getIntent().data?.let { data = it }
            getIntent().getStringExtra("accountType")?.let { putExtra("accountType", it) }
            getIntent().getBooleanExtra("confirmCredentials", false).let { putExtra("confirmCredentials", it) }
        }
        startActivity(intent)
        finish()
    }
}
