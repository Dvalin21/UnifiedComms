package com.unifiedcomms.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.unifiedcomms.R

class OAuthCallbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = intent
        val uri = intent.data
        
        // Forward to AddAccountActivity which handles the callback
        val forwardIntent = Intent(this, AddAccountActivity::class.java)
        forwardIntent.data = uri
        forwardIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(forwardIntent)
        
        finish()
    }
}