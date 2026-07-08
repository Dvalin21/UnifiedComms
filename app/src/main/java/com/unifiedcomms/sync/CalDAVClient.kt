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
    private val client: OkHttpClient,
    private val bearerToken: String? = null
) {
    companion object {
        private const val TAG = "CalDAVClient"
        val ICAL_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        val VCARD_MEDIA_TYPE = "text/vcard; charset=utf-8".toMediaType()
    }

    private val auth = if (bearerToken != null) "Bearer $bearerToken" else Credentials.basic(username, password)
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

    suspend fun fetchItem(@Suppress("UNUSED_PARAMETER") accountId: String, href: String): IcsResource? = withContext(Dispatchers.IO) {
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

    // ponytail: task lists are CalDAV collections that advertise VTODO in their component set.
    suspend fun discoverTaskLists(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val principal = tryFindPrincipalAt(baseUrl) ?: return@withContext emptyList()
        val homeSet = findCalendarHomeSet(principal) ?: principal
        val out = mutableListOf<CalendarInfo>()
        scanForTaskLists(homeSet, out)
        out
    }

    private suspend fun scanForTaskLists(url: String, result: MutableList<CalendarInfo>, visited: MutableSet<String> = mutableSetOf()) {
        val normalized = url.trimEnd('/')
        if (normalized in visited) return
        visited += normalized
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
                <C:supported-calendar-component-set/>
              </D:prop>
            </D:propfind>
        """.trimIndent()
        try {
            val body = propfind(normalized, xml, depth = "1")
            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(body.byteInputStream())
            val responses = db.getElementsByTagName("response")
            for (i in 0 until responses.length) {
                val resp = responses.item(i) as? Element ?: continue
                val href = resp.getElementsByTagName("href").item(0)?.textContent?.trim().orEmpty()
                val fullUrl = if (href.startsWith("http://", true) || href.startsWith("https://", true)) href else "$baseUrl/${href.removePrefix("/")}"
                val subUrl = fullUrl.trimEnd('/')
                if (subUrl == normalized) continue
                val types = mutableListOf<String>()
                var rt = resp.getElementsByTagName("resourcetype").item(0) as? Element
                var node = rt?.firstChild
                while (node != null) {
                    if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) types += node.localName.lowercase()
                    node = node.nextSibling
                }
                val comps = componentSetOf(resp)
                if (!types.contains("calendar") || !comps.contains("VTODO")) {
                    scanForTaskLists(subUrl, result, visited)
                    continue
                }
                val name = resp.getElementsByTagName("displayname").item(0)?.textContent?.trim().orEmpty()
                    .ifBlank { subUrl.split("/").lastOrNull { it.isNotBlank() }.orEmpty() }
                result += CalendarInfo(path = subUrl, displayName = name, supportsVTODO = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "task list scan failed", e)
        }
    }

    private fun componentSetOf(response: Element): Set<String> {
        val set = response.getElementsByTagName("supported-calendar-component-set").item(0) as? Element ?: return emptySet()
        val comps = set.getElementsByTagName("comp")
        val out = mutableSetOf<String>()
        for (i in 0 until comps.length) {
            val name = (comps.item(i) as? Element)?.getAttribute("name")?.trim()?.uppercase().orEmpty()
            if (name.isNotBlank()) out += name
        }
        return out
    }

    // ponytail: generic PUT used for task writes (VTODO). Returns server ETag or null on failure.
    suspend fun putResource(href: String, body: String, contentType: okhttp3.MediaType = ICAL_MEDIA_TYPE): String? = withContext(Dispatchers.IO) {
        val target = when {
            href.startsWith("http://", true) || href.startsWith("https://", true) -> href
            else -> "$baseUrl/${href.removePrefix("/")}"
        }
        val req = Request.Builder().url(target).put(body.toRequestBody(contentType)).build()
        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "PUT $target failed: ${resp.code}")
                return@use null
            }
            resp.header("ETag")?.trim('"')?.ifBlank { null }
        }
    }

    suspend fun deleteResource(href: String): Boolean = withContext(Dispatchers.IO) {
        val target = when {
            href.startsWith("http://", true) || href.startsWith("https://", true) -> href
            else -> "$baseUrl/${href.removePrefix("/")}"
        }
        val req = Request.Builder().url(target).delete().build()
        internalClient.newCall(req).execute().use { resp -> resp.isSuccessful || resp.code == 404 }
    }

    // ponytail: CardDAV reuses the same DAV plumbing. An addressbook is a collection
    // whose resourcetype contains "addressbook". vCard GET/PUT/DELETE use putResource/deleteResource.
    data class AddressBookInfo(val path: String, val displayName: String)

    suspend fun discoverAddressBooks(): List<AddressBookInfo> = withContext(Dispatchers.IO) {
        val principal = tryFindPrincipalAt(baseUrl) ?: return@withContext emptyList()
        val homeSet = findAddressBookHomeSet(principal) ?: principal
        val out = mutableListOf<AddressBookInfo>()
        scanForAddressBooks(homeSet, out)
        out
    }

    private suspend fun findAddressBookHomeSet(principal: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:carddav">
                  <D:prop><C:addressbook-home-set/></D:prop>
                </D:propfind>
            """.trimIndent()
            val resp = propfind(principal, xml, depth = "0")
            parseHrefsFromPropfind(resp).firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun scanForAddressBooks(url: String, result: MutableList<AddressBookInfo>, visited: MutableSet<String> = mutableSetOf()) {
        val normalized = url.trimEnd('/')
        if (normalized in visited) return
        visited += normalized
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:carddav">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
              </D:prop>
            </D:propfind>
        """.trimIndent()
        try {
            val body = propfind(normalized, xml, depth = "1")
            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(body.byteInputStream())
            val responses = db.getElementsByTagName("response")
            for (i in 0 until responses.length) {
                val resp = responses.item(i) as? Element ?: continue
                val href = resp.getElementsByTagName("href").item(0)?.textContent?.trim().orEmpty()
                val fullUrl = if (href.startsWith("http://", true) || href.startsWith("https://", true)) href else "$baseUrl/${href.removePrefix("/")}"
                val subUrl = fullUrl.trimEnd('/')
                if (subUrl == normalized) continue
                val types = mutableListOf<String>()
                var rt = resp.getElementsByTagName("resourcetype").item(0) as? Element
                var node = rt?.firstChild
                while (node != null) {
                    if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) types += node.localName.lowercase()
                    node = node.nextSibling
                }
                if (types.contains("addressbook")) {
                    val name = resp.getElementsByTagName("displayname").item(0)?.textContent?.trim().orEmpty()
                        .ifBlank { subUrl.split("/").lastOrNull { it.isNotBlank() }.orEmpty() }
                    result += AddressBookInfo(path = subUrl, displayName = name)
                } else {
                    scanForAddressBooks(subUrl, result, visited)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "addressbook scan failed", e)
        }
    }

    suspend fun listAddressBookItems(addressBookPath: String): List<ETagEntry> = withContext(Dispatchers.IO) {
        val target = if (addressBookPath.startsWith("http://", true) || addressBookPath.startsWith("https://", true)) addressBookPath else "$baseUrl/${addressBookPath.removePrefix("/")}"
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:getetag/></D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)
        val req = Request.Builder().url(target).method("PROPFIND", xml).headers(mapOf("Depth" to "1").toHeaders()).build()
        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return@use emptyList()
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
                    // ponytail: skip the collection itself (no .vcf extension).
                    if (href.isNotBlank() && etag.isNotBlank() && href.endsWith(".vcf", true)) out += ETagEntry(href, etag)
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // ponytail: GET a single vCard resource (text/vcard). Reuses fetchItem shape but tagged for clarity.
    suspend fun fetchVCard(href: String): IcsResource? = fetchItem("", href)

    // ponytail: PUT a vCard. Reuses putResource with the vCard media type.
    suspend fun putVCard(href: String, body: String): String? = putResource(href, body, VCARD_MEDIA_TYPE)

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
