package com.unifiedcomms

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.unifiedcomms.ui.main.MainActivity
import java.util.Properties
import javax.mail.Session
import javax.mail.Store
import android.util.Log

/**
 * Reproduces the app's EXACT IMAP connect path on-device (Conscrypt + tablet net),
 * using dummy creds. The timeout you hit is at the TCP/TLS stage BEFORE LOGIN, so a
 * wrong password still exercises the exact failure. Times each phase to localize it.
 */
class ImapProbeTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun probeConnect(): Unit {
        val host = "imap.houseofmanns.com"
        val port = 993
        val acceptAll = false // your default from the UI

        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", host)
            put("mail.imap.port", port)
            put("mail.imap.ssl.enable", true)
            put("mail.imap.auth", true)
            put("mail.imap.connectiontimeout", 60000)
            put("mail.imap.timeout", 300000)
            if (acceptAll) {
                put("mail.imap.ssl.checkserveridentity", false)
                val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf(object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(c: Array<out java.security.cert.X509Certificate>?, a: String?) {}
                    override fun checkServerTrusted(c: Array<out java.security.cert.X509Certificate>?, a: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                }), java.security.SecureRandom())
                put("mail.imap.ssl.socketFactory", ctx.socketFactory)
            }
        }
        val session = Session.getInstance(props)
        val store: Store = session.store
        Log.e("IMAPPROBE", "session built; acceptAll=$acceptAll; store class=${store.javaClass.name}")

        val t0 = System.currentTimeMillis()
        try {
            Log.e("IMAPPROBE", "calling store.connect (TCP+TLS)...")
            store.connect(host, port, "probe@houseofmanns.com", "dummy-password")
            Log.e("IMAPPROBE", "CONNECT OK in ${System.currentTimeMillis() - t0}ms")
        } catch (e: Exception) {
            val dt = System.currentTimeMillis() - t0
            Log.e("IMAPPROBE", "CONNECT FAILED after ${dt}ms: class=${e.javaClass.name} msg='${e.message}'")
            e.printStackTrace()
            // unwrap cause chain
            var c = e.cause
            var depth = 0
            while (c != null && depth < 5) {
                Log.e("IMAPPROBE", "  cause[$depth]: ${c.javaClass.name}: ${c.message}")
                c = c.cause; depth++
            }
        }
    }
}
