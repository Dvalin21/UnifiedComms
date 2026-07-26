package com.unifiedcomms

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.unifiedcomms.ui.main.MainActivity
import javax.mail.Session
import javax.mail.Store
import java.util.Properties

/**
 * Reproduces the app's exact JavaMail IMAPS connect to imap.houseofmanns.com:993
 * from the DEVICE context (Conscrypt + network). Dummy creds are fine — the
 * failure we care about is at the TCP/TLS layer, before IMAP LOGIN. The exception
 * message reveals the true root cause of the "Couldn't connect, timeout 60000".
 */
class ImapConnectProbeTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun probeImap993(): Unit = runBlocking {
        val host = "imap.houseofmanns.com"
        val tries = listOf(
            Triple("993 SSL", 993, true),
            Triple("143 STARTTLS", 143, false),
        )
        for ((label, port, ssl) in tries) {
            try {
                val props = Properties().apply {
                    put("mail.store.protocol", "imap")
                    put("mail.imap.host", host)
                    put("mail.imap.port", port)
                    put("mail.imap.ssl.enable", ssl)
                    put("mail.imap.starttls.enable", !ssl)
                    put("mail.imap.connectiontimeout", 15000)
                    put("mail.imap.timeout", 15000)
                }
                val session = Session.getInstance(props)
                val store: Store = session.getStore("imap")
                Log.e("PROBE", "=== $label : connecting ===")
                store.connect(host, "probe@houseofmanns.com", "dummy")
                Log.e("PROBE", "=== $label : CONNECTED OK (auth would be next) ===")
                store.close()
            } catch (e: Exception) {
                Log.e("PROBE", "=== $label : FAIL class=${e.javaClass.name} msg='${e.message}' ===")
                e.printStackTrace()
            }
        }
    }
}
