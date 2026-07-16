package com.unifiedcomms.util

import android.util.Log
import com.unifiedcomms.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Minimal email autoconfig/autodiscover. Tries (in order):
 *  1. Thunderbird central autoconfig:  autoconfig.thunderbird.net/mail/config-v1.1.xml?emailaddress=
 *  2. Domain .well-known:           <domain>/.well-known/mail/config-v1.1.xml
 * Returns a ServerConfig on success, or null if nothing was found / network failed.
 * Caller decides fallback to manual advanced fields. ponytail: no MS autodiscover POST yet.
 */
object Autodiscover {
    private const val TAG = "Autodiscover"
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class Discovered(
        val imapHost: String,
        val imapPort: Int,
        val imapSsl: Boolean,
        val smtpHost: String,
        val smtpPort: Int,
        val smtpStartTls: Boolean
    )

    suspend fun discover(email: String): Discovered? = withContext(Dispatchers.IO) {
        val domain = email.substringAfter('@').lowercase().trim()
        if (domain.isBlank() || !domain.contains('.')) return@withContext null
        val encoded = java.net.URLEncoder.encode(email, "UTF-8")
        val urls = listOf(
            "https://autoconfig.thunderbird.net/mail/config-v1.1.xml?emailaddress=$encoded",
            "https://$domain/.well-known/mail/config-v1.1.xml"
        )
        for (url in urls) {
            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "UnifiedComms")
                    .build()
                val body = http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: continue
                parse(body)?.let { return@withContext it }
            } catch (e: Exception) {
                Log.d(TAG, "autoconfig miss at $url: ${e.message}")
            }
        }
        null
    }

    private fun parse(xml: String): Discovered? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var imapHost: String? = null
            var imapPort = 993
            var imapSsl = true
            var smtpHost: String? = null
            var smtpPort = 587
            var smtpStartTls = true
            var event = parser.eventType
            var inIncoming = false
            var inOutgoing = false
            var currentType: String? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        when {
                            name == "incomingserver" -> {
                                inIncoming = true
                                currentType = parser.getAttributeValue(null, "type")?.lowercase()
                            }
                            name == "outgoingserver" -> {
                                inOutgoing = true
                                currentType = parser.getAttributeValue(null, "type")?.lowercase()
                            }
                            name == "hostname" -> {
                                val v = parser.nextText().trim()
                                if (inIncoming && currentType == "imap") imapHost = v
                                if (inOutgoing && currentType == "smtp") smtpHost = v
                            }
                            name == "port" -> {
                                val v = parser.nextText().trim().toIntOrNull() ?: 0
                                if (inIncoming && currentType == "imap" && v > 0) imapPort = v
                                if (inOutgoing && currentType == "smtp" && v > 0) smtpPort = v
                            }
                            name == "sockettype" -> {
                                val v = parser.nextText().trim().lowercase()
                                val ssl = v.contains("ssl")
                                if (inIncoming && currentType == "imap") imapSsl = ssl
                                if (inOutgoing && currentType == "smtp") smtpStartTls = !ssl && v.contains("starttls")
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "incomingserver") { inIncoming = false; currentType = null }
                        if (name == "outgoingserver") { inOutgoing = false; currentType = null }
                    }
                }
                event = parser.next()
            }
            if (imapHost != null && smtpHost != null) {
                Discovered(imapHost, imapPort, imapSsl, smtpHost, smtpPort, smtpStartTls)
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "parse failed: ${e.message}")
            null
        }
    }

    fun toServerConfig(d: Discovered, server: String): ServerConfig {
        return ServerConfig(
            imapHost = d.imapHost,
            imapPort = d.imapPort,
            imapUseSsl = d.imapSsl,
            smtpHost = d.smtpHost,
            smtpPort = d.smtpPort,
            smtpUseStartTls = d.smtpStartTls,
            caldavUrl = "$server/dav/",
            carddavUrl = "$server/dav/"
        )
    }
}
