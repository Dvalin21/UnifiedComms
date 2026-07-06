package com.unifiedcomms.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.w3c.dom.NodeList

class CalDAVClient(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    companion object {
        private const val TAG = "CalDAVClient"
        private val XML_MEDIA = "application/xml; charset=utf-8".toMediaType()
    }

    private val auth = Credentials.basic(username, password)

    suspend fun listCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val body = """<?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
              </D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA)

        val req = Request.Builder()
            .url(baseUrl)
            .method("PROPFIND", body)
            .headers(mapOf("Authorization" to auth, "Depth" to "1").toHeaders())
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val text = resp.body?.string().orEmpty()
            val hrefs = parseHrefs(text)
            hrefs.mapIndexed { idx, href ->
                CalendarInfo(
                    id = href.ifBlank { java.util.UUID.randomUUID().toString() },
                    serverId = href,
                    name = href.substringAfterLast('/').ifBlank { "Calendar $idx" }
                )
            }
        }
    }

    suspend fun listCalendarItems(calendarPath: String): List<String> = withContext(Dispatchers.IO) {
        val target = if (calendarPath.isBlank()) baseUrl else calendarPath
        val body = """<?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:getetag/><D:calendar-data/></D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA)

        val req = Request.Builder()
            .url(target)
            .method("PROPFIND", body)
            .headers(mapOf("Authorization" to auth, "Depth" to "1").toHeaders())
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val text = resp.body?.string().orEmpty()
            parseHrefs(text).filter { it.isNotBlank() }
        }
    }

    suspend fun fetchItem(account: com.unifiedcomms.data.model.Account, href: String): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val target = when {
            href.startsWith("http://", true) || href.startsWith("https://", true) -> href
            else -> baseUrl.removeSuffix("/") + "/" + href.removePrefix("/")
        }
        val req = Request.Builder()
            .url(target)
            .header("Authorization", auth)
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val etag = resp.header("ETag")?.trim('"') ?: ""
            val ical = resp.body?.string().orEmpty()
            ical to etag.hashCode()
        }
    }

    private fun parseHrefs(xml: String): List<String> {
        return try {
            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xml.byteInputStream())
            val list = mutableListOf<String>()
            val nodes: NodeList = db.getElementsByTagNameNS("*", "href")
            for (i in 0 until nodes.length) {
                val n: Node = nodes.item(i)
                list.add(n.textContent.trim())
            }
            list
        } catch (t: Throwable) {
            Log.w(TAG, "parseHrefs failed", t)
            emptyList()
        }
    }
}

data class CalendarInfo(
    val id: String,
    val serverId: String,
    val name: String
)
