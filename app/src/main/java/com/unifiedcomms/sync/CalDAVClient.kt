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
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

class CalDAVClient(
    serverUrl: String,
    private val username: String,
    private val password: String,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "CalDAVClient"
        val ICAL_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
    }

    private val auth = Credentials.basic(username, password)
    private val baseUrl: String = serverUrl.trimEnd('/')
    private val internalClient: OkHttpClient = client.newBuilder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", auth)
                .header("User-Agent", "UnifiedComms/1.0 (CalDAV)")
                .build()
            chain.proceed(req)
        }
        .build()

    data class CalendarInfo(val path: String, val displayName: String, val ctag: String = "", val supportsVTODO: Boolean = false)

    data class ETagEntry(val href: String, val etag: String)

    data class IcsResource(val href: String, val etag: String, val ical: String)

    suspend fun listCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
              </D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)

        val req = Request.Builder()
            .url(baseUrl)
            .method("PROPFIND", body)
            .headers(mapOf("Depth" to "1").toHeaders())
            .build()

        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val text = resp.body?.string().orEmpty()
            val hrefs = parseHrefs(text)
            hrefs.mapIndexed { idx, href ->
                CalendarInfo(
                    path = href,
                    displayName = href.substringAfterLast('/').ifBlank { "Calendar ${idx + 1}" },
                    ctag = ""
                )
            }
        }
    }

    suspend fun discoverCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val principal = tryFindPrincipalAt(baseUrl) ?: return@withContext emptyList()
        val homeSet = findCalendarHomeSet(principal)
        val target = homeSet ?: principal
        val calendars = mutableListOf<CalendarInfo>()
        scanForCalendars(target, calendars)
        if (calendars.isEmpty()) calendars += CalendarInfo(principal, "Calendar", "", true)
        calendars
    }

    private suspend fun tryFindPrincipalAt(url: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:">
                  <D:prop><D:current-user-principal/></D:prop>
                </D:propfind>
            """.trimIndent()
            val resp = propfind(url, xml, depth = "0")
            parseHrefsFromPropfind(resp).firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun findCalendarHomeSet(principal: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                  <D:prop><C:calendar-home-set/></D:prop>
                </D:propfind>
            """.trimIndent()
            val resp = propfind(principal, xml, depth = "0")
            parseHrefsFromPropfind(resp).firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun scanForCalendars(url: String, result: MutableList<CalendarInfo>, visited: MutableSet<String> = mutableSetOf()) {
        if (!result.none { it.path == url }) return
        val normalized = url.trimEnd('/')
        if (normalized in visited) return
        visited += normalized

        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav" xmlns:CS="http://calendarserver.org/ns/">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
                <CS:getctag/>
                <C:supported-calendar-component-set/>
              </D:prop>
            </D:propfind>
        """.trimIndent()

        try {
            val body = propfind(normalized, xml, depth = "1")
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(body.byteInputStream())
            val responses = doc.getElementsByTagName("response")
            val allByName = doc.getElementsByTagName("displayname")
            for (i in 0 until responses.length) {
                val resp = responses.item(i) as? Element ?: continue
                val hrefNode = resp.getElementsByTagName("href").item(0)
                val href = hrefNode?.textContent?.trim().orEmpty()
                val fullUrl = if (href.startsWith("http://", true) || href.startsWith("https://", true)) href else "$baseUrl/${href.removePrefix("/")}"
                val subUrl = fullUrl.trimEnd('/')
                if (subUrl == normalized) continue

                val types = mutableListOf<String>()
                var child = resp.getElementsByTagName("resourcetype").item(0) as? Element
                var node = child?.firstChild
                while (node != null) {
                    if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) types += node.localName.lowercase()
                    node = node.nextSibling
                }
                if (!types.contains("calendar")) {
                    scanForCalendars(subUrl, result, visited)
                    continue
                }
                val nameNode = resp.getElementsByTagName("displayname").item(0)
                val name = nameNode?.textContent?.trim().orEmpty().ifBlank { href.split("/").lastOrNull { it.isNotBlank() }.orEmpty() }
                val ctagNode = resp.getElementsByTagName("getctag").item(0)
                val ctag = ctagNode?.textContent?.trim().orEmpty()
                result += CalendarInfo(path = subUrl, displayName = name, ctag = ctag, supportsVTODO = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "calendar scan failed", e)
        }
    }

    suspend fun listCalendarItems(calendarPath: String): List<String> = withContext(Dispatchers.IO) {
        val target = if (calendarPath.startsWith("http://", true) || calendarPath.startsWith("https://", true)) calendarPath else "$baseUrl/${calendarPath.removePrefix("/")}"
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:getetag/><D:calendar-data/></D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)

        val req = Request.Builder()
            .url(target)
            .method("PROPFIND", body)
            .headers(mapOf("Depth" to "1").toHeaders())
            .build()

        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val text = resp.body?.string().orEmpty()
            parseHrefs(text).filter { it.isNotBlank() }
        }
    }

    suspend fun getETagList(calendarPath: String): List<ETagEntry> = withContext(Dispatchers.IO) {
        val target = if (calendarPath.startsWith("http://", true) || calendarPath.startsWith("https://", true)) calendarPath else "$baseUrl/${calendarPath.removePrefix("/")}"
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:getetag/></D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)

        val req = Request.Builder()
            .url(target)
            .method("PROPFIND", body)
            .headers(mapOf("Depth" to "1").toHeaders())
            .build()

        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return@withContext emptyList()
            try {
                val db = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(text.byteInputStream())
                val responses = db.getElementsByTagName("response")
                val hrefNodes = db.getElementsByTagName("href")
                val etagNodes = db.getElementsByTagName("getetag")
                val out = mutableListOf<ETagEntry>()
                for (i in 0 until responses.length) {
                    if (i >= hrefNodes.length || i >= etagNodes.length) break
                    val href = hrefNodes.item(i)?.textContent?.trim().orEmpty()
                    val etag = etagNodes.item(i)?.textContent?.trim('"').orEmpty()
                    if (href.isNotBlank() && etag.isNotBlank()) out += ETagEntry(href, etag)
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun fetchItem(accountId: String, href: String): IcsResource? = withContext(Dispatchers.IO) {
        val target = when {
            href.startsWith("http://", true) || href.startsWith("https://", true) -> href
            else -> "$baseUrl/${href.removePrefix("/")}"
        }
        internalClient.newCall(Request.Builder().url(target).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val etag = resp.header("ETag")?.trim('"') ?: ""
            val ical = resp.body?.string().orEmpty()
            IcsResource(href, etag, ical)
        }
    }

    suspend fun getCTag(calendarPath: String): String = withContext(Dispatchers.IO) {
        val target = if (calendarPath.startsWith("http://", true) || calendarPath.startsWith("https://", true)) calendarPath else "$baseUrl/${calendarPath.removePrefix("/")}"
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:CS="http://calendarserver.org/ns/">
              <D:prop><CS:getctag/></D:prop>
            </D:propfind>
        """.trimIndent()
        try {
            val resp = propfind(target, xml, depth = "0")
            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resp.byteInputStream())
            val node = db.getElementsByTagName("getctag").item(0)
            node?.textContent?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun propfind(url: String, body: String, depth: String = "1"): String {
        val req = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth)
            .build()
        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.message}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun parseHrefs(xml: String): List<String> {
        return try {
            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
            val nodes = db.getElementsByTagName("href")
            List(nodes.length) { i -> nodes.item(i).textContent.trim() }
        } catch (t: Throwable) {
            Log.w(TAG, "parseHrefs failed", t)
            emptyList()
        }
    }

    private fun parseHrefsFromPropfind(xml: String): List<String> {
        return try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            val out = mutableListOf<String>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("href", ignoreCase = true)) {
                    out += parser.nextText().trim()
                }
                event = parser.next()
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
