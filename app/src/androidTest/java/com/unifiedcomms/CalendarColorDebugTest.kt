package com.unifiedcomms

import android.app.Application
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalDAVClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import java.util.concurrent.TimeUnit

class CalendarColorDebugTest {
    @Test
    fun probeColors(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())

        val accounts = accountRepo.getAllActive().first()
        Log.e("COLORDBG", "accounts found=${accounts.size}")
        val acc = accounts.firstOrNull { it.accountType == com.unifiedcomms.data.model.AccountType.MAILCOW }
            ?: accounts.firstOrNull()
        if (acc == null) { Log.e("COLORDBG", "NO ACCOUNT"); return@runBlocking }
        val auth = crypto.decryptAuthConfig(acc.authConfig)
        val url = acc.serverConfig.caldavUrl ?: run { Log.e("COLORDBG", "NO CALDAV URL"); return@runBlocking }
        Log.e("COLORDBG", "account=${acc.email} caldavUrl=$url")

        // 1) What does the server's PROPFIND actually return for calendar-color?
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        val xmlStr = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav" xmlns:IC="http://apple.com/ns/ical/" xmlns:A="http://apple.com/ns/ical/">
  <D:prop>
    <D:displayname/>
    <D:resourcetype/>
    <IC:calendar-color/>
    <A:calendar-color/>
    <C:calendar-color/>
  </D:prop>
</D:propfind>""".trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())
        val pr = Request.Builder().url(url).method("PROPFIND", xmlStr)
            .header("Authorization", Credentials.basic(auth.username ?: acc.email, auth.passwordEncrypted ?: ""))
            .header("Depth", "1").header("User-Agent", "UnifiedComms/1.0 (CalDAV)").build()
        runCatching {
            val resp = client.newCall(pr).execute()
            val body = resp.body?.string().orEmpty()
            Log.e("COLORDBG", "PROPFIND status=${resp.code}")
            // print only color-relevant + displayname lines
            body.lines().filter { it.contains("calendar-color", true) || it.contains("displayname", true) }
                .forEach { Log.e("COLORDBG", "RAWLINE: $it") }
        }.onFailure { Log.e("COLORDBG", "PROPFIND EXC ${it.message}") }

        // 2) CalDAVClient parsed collection color
        val calDav = CalDAVClient(url, auth.username ?: acc.email, auth.passwordEncrypted ?: "", client)
        runCatching {
            val cals = calDav.discoverCalendars()
            cals.forEach { Log.e("COLORDBG", "DISCOVERED path=${it.path} name=${it.displayName} color='${it.color}'") }
        }.onFailure { Log.e("COLORDBG", "DISCOVER EXC ${it.javaClass.name} '${it.message}'") }

        // 3) Stored event colors
        val events = calendarRepo.getAllEventsForAccount(acc.id).first()
        Log.e("COLORDBG", "STORED events=${events.size}")

        // 4) Of events in the CURRENT LOADED YEAR (2026+), how many are non-blue?
        val yearStart = java.time.LocalDate.of(2026, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yearEnd = java.time.LocalDate.of(2027, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val inYear = events.filter {
            runCatching { it.startAt.toInstant(kotlinx.datetime.TimeZone.currentSystemDefault()).toEpochMilliseconds() }.getOrNull()?.let { ms -> ms in yearStart..yearEnd } ?: false
        }
        val inYearBlue = inYear.count { it.color.background.equals("#2196F3", true) }
        val inYearNamed = inYear.count { !it.color.background.startsWith("#") }
        Log.e("COLORDBG", "INYEAR total=${inYear.size} blue=${inYearBlue} named=${inYearNamed}")
        inYear.filter { !it.color.background.equals("#2196F3", true) }.take(12).forEach {
            Log.e("COLORDBG", "YEAR_NONBLUE title='${it.title}' color='${it.color.background}' start='${it.startAt}'")
        }
    }
}
