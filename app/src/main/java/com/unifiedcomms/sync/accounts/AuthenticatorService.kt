package com.unifiedcomms.sync.accounts

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Hosts [UnifiedCommsAuthenticator] for the system AccountManager.
 * The old provider-based path never overrode onBind, so sync/accounts via
 * Settings > Accounts could never reach it.
 */
class AuthenticatorService : Service() {
    override fun onBind(intent: Intent?): IBinder? =
        (UnifiedCommsAuthenticator(this)).iBinder
}
