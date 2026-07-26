package com.unifiedcomms

import android.app.Application
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalDAVClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test

/**
 * Diagnose duplicate/stale calendar rows on-device. For each recurring title,
 * dumps every stored master row's key fields so we can see whether one logical
 * event became N distinct rows (dedup failure) and why.
 */
class CalendarDupeDebugTest {
    @Test
    fun dumpDupes(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())

        val accounts = accountRepo.getAllActive().first()
        Log.e("DUPEDBG", "accounts=${accounts.size}")
        for (acc in accounts) {
            val events = calendarRepo.getAllEventsForAccount(acc.id).first()
            val masters = events.filter { it.isMaster() }
            Log.e("DUPEDBG", "ACC ${acc.email} total=${events.size} masters=${masters.size}")
            // group masters by (title + recurrenceRule + dtstart) -> real logical events
            val byKey = masters.groupBy { "${it.title.trim()}||${it.recurrenceRule}||${it.startAt}" }
            val dupes = byKey.filter { it.value.size > 1 }
            Log.e("DUPEDBG", "LOGICAL_GROUPS=${byKey.size} DUP_GROUPS=${dupes.size}")
            dupes.forEach { (key, rows) ->
                Log.e("DUPEDBG", "DUPE_KEY=$key")
                rows.forEach { e ->
                    Log.e("DUPEDBG", "  ROW uid='${e.uid}' id='${e.id}' calId='${e.calendarId}' etag='${e.etag}' color='${e.color.background}'")
                }
            }
            // rows sharing the exact same uid (true dupes)
            val byUid = masters.groupBy { it.uid }
            val uidDupes = byUid.filter { it.value.size > 1 }
            Log.e("DUPEDBG", "UID_DUPES=${uidDupes.size}")
            uidDupes.forEach { (uid, rows) ->
                Log.e("DUPEDBG", "  UID_DUPE uid='$uid' count=${rows.size} ids=${rows.map { it.id }}")
            }
            //Dump "Appointment with Maggie" masters (explain on-screen triplication)
            val maggie = masters.filter { it.title.contains("Maggie", true) }
            Log.e("DUPEDBG", "MAGGIE_MASTERS=${maggie.size}")
            maggie.forEach { e ->
                Log.e("DUPEDBG", "  MAGGIE uid='${e.uid}' start='${e.startAt}' rrule='${e.recurrenceRule}' calId='${e.calendarId}' color='${e.color.background}'")
            }
            // Color distribution of July 2026 window events (what the screen shows)
            val julStart = java.time.LocalDate.of(2026,7,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val julEnd = java.time.LocalDate.of(2026,8,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val jul = events.filter {
                runCatching { it.startAt.toInstant().toEpochMilliseconds() }.getOrNull()?.let { ms -> ms in julStart..julEnd } ?: false
            }
            val julByColor = jul.groupBy { it.color.background }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
            Log.e("DUPEDBG", "JULY2026 events=${jul.size} colors=${julByColor}")
            // distinct titles in July that appear >1x (look like dupes)
            val julTitles = jul.groupBy { it.title }.filter { it.value.size > 1 }
            Log.e("DUPEDBG", "JULY_TITLE_DUPES=${julTitles.size}")
            julTitles.forEach { (t, rows) ->
                Log.e("DUPEDBG", "  JUL_TITLE '$t' count=${rows.size} uids=${rows.map { it.uid }}")
            }
        // STALE CHECK: non-recurring masters whose original start is before 2026 (genuinely old, not repeating)
        val staleNonRecur = masters.filter {
            it.recurrenceRule == null && runCatching { it.startAt.toInstant().toEpochMilliseconds() }.getOrNull()?.let { ms ->
                ms < java.time.LocalDate.of(2026,1,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } ?: false
        }
        Log.e("DUPEDBG", "STALE_NONRECUR(count=${staleNonRecur.size})")
        staleNonRecur.take(15).forEach { e ->
            Log.e("DUPEDBG", "  STALE title='${e.title}' start='${e.startAt}' uid='${e.uid}'")
        }
        // How many MASTERS have original start before 2026 but are recurring (legit repeaters like Maggie)?
        val oldStartRecur = masters.filter {
            it.recurrenceRule != null && runCatching { it.startAt.toInstant().toEpochMilliseconds() }.getOrNull()?.let { ms ->
                ms < java.time.LocalDate.of(2026,1,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } ?: false
        }
        Log.e("DUPEDBG", "OLDSTART_RECUR(count=${oldStartRecur.size}) e.g.=${oldStartRecur.take(3).map { it.title to it.startAt }}")
        // total masters and their original-start year distribution
        val yrDist = masters.groupBy { runCatching { it.startAt.toInstant().toEpochMilliseconds() }.getOrNull()?.let { ms ->
            java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).year } ?: -1 }.mapValues { it.value.size }
        Log.e("DUPEDBG", "MASTER_YEAR_DIST=$yrDist")
        // GROUND TRUTH: what does getEventsInRangeUnified (exactly what MonthView receives) return for July?
        val accIds = accounts.map { it.id }
        val rendered = calendarRepo.getEventsInRangeUnified(accIds, julStart, julEnd).first()
        Log.e("DUPEDBG", "RENDERED_JULY total=${rendered.size}")
        rendered.groupBy { it.color.background }.toList().sortedByDescending { it.second.size }.forEach { (c, rows) ->
            Log.e("DUPEDBG", "  RENDERED_COLOR c='$c' count=${rows.size} titles=${rows.take(3).map { it.title }}")
        }
        // Replicate MonthView EXACTLY: what int does ev.color.toColorInt() produce per rendered event?
        val barIntCounts = rendered.groupingBy { String.format("#%06X", 0xFFFFFF and it.color.toColorInt()) }.eachCount().toList().sortedByDescending { it.second }
        Log.e("DUPEDBG", "BARINT_SUMMARY=${barIntCounts.map { it.first+"="+it.second }.joinToString(",")}")
        // DIRECT proof: what does the INSTALLED toColorInt() return for real server colors?
        val samples = listOf("dodgerblue", "purple", "seagreen", "maroon", "#FF0000FF", "#2196F3", "gold")
        for (s in samples) {
            val c = com.unifiedcomms.data.model.EventColor(s, "#FFFFFF")
            val int = c.toColorInt()
            val hex = String.format("#%06X", 0xFFFFFF and int)
            Log.e("DUPEDBG", "TOCOLOR s='$s' -> hex='$hex' norm='${com.unifiedcomms.ui.theme.ColorNormalizer.normalize(s)}'")
        }
        }
    }

    @Test
    fun probeServerLive(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val accounts = accountRepo.getAllActive().first()
        for (acc in accounts) {
            val auth = crypto.decryptAuthConfig(acc.authConfig)
            val url = acc.serverConfig.caldavUrl ?: continue
            val client = okhttp3.OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
            val dav = CalDAVClient(url, auth.username ?: "", auth.passwordEncrypted ?: "", client)
            val cals = dav.discoverCalendars()
            Log.e("PROBEDBG", "ACC ${acc.email} caldav=$url calendars=${cals.map { it.path }}")
            for (cal in cals) {
                val etags = dav.getETagList(cal.path)
                val google = etags.filter { it.href.contains("@google.com", true) || it.href.contains("maggie", true) }
                Log.e("PROBEDBG", "CAL ${cal.path} totalHrefs=${etags.size} googleOrMaggie=${google.size} e.g.=${google.take(5).map { it.href }}")
            }
        }
    }

    @Test
    fun dumpRecurring(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val accountRepo = AccountRepositoryImpl(db.accountDao(), CryptoManagerImpl(app))
        val accounts = accountRepo.getAllActive().first()
        val accIds = accounts.map { it.id }
        val julStart = java.time.LocalDate.of(2026,7,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val julEnd = java.time.LocalDate.of(2026,8,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = calendarRepo.getAllEventsForAccount(accounts.first().id).first()
        val masters = events.filter { it.isMaster() }
        val want = listOf("guitar","bible","praise","worship","monday")
        for (m in masters) {
            val t = m.title.lowercase()
            if (!want.any { t.contains(it) }) continue
            Log.e("RECDBG", "TITLE='${m.title}' uid=${m.uid.take(20)} status=${m.status} tz=${m.startAt.timeZone} allDay=${m.isAllDay()} start=${m.startAt.dateTime ?: m.startAt.date} rule=${m.recurrenceRule}")
        }
        // Now what July occurrences does the UI actually compute?
        val expanded = calendarRepo.getEventsInRangeUnified(accIds, julStart, julEnd).first()
        val byTitle = expanded.groupBy { it.title.lowercase() }
        for ((title, list) in byTitle) {
            if (!want.any { title.contains(it) }) continue
            val days = list.map { ev ->
                val zid = java.time.ZoneId.of(com.unifiedcomms.data.model.TimeZoneUtil.normalize(ev.startAt.timeZone) ?: "UTC")
                val ldt = java.time.Instant.ofEpochMilli(ev.startAt.toInstant(com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(ev.startAt.timeZone)).toEpochMilliseconds()).atZone(zid).toLocalDateTime()
                ldt.dayOfWeek.toString()
            }.distinct().sorted()
            Log.e("RECDBG", "JULY '${list.first().title}' -> days=$days count=${list.size} sampleStatus=${list.first().status}")
        }
    }

    @Test
    fun dumpDiagnose(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val accountRepo = AccountRepositoryImpl(db.accountDao(), CryptoManagerImpl(app))
        val accounts = accountRepo.getAllActive().first()
        val aid = accounts.first().id
        val all = calendarRepo.getAllEventsForAccount(aid).first()
        // (A) cancelled analysis
        val byStatus = all.groupingBy { it.status }.eachCount()
        val cancelledStatus = all.filter { it.status == com.unifiedcomms.data.model.EventStatus.CANCELLED }
        val isCancelledTrue = all.filter { it.isCancelled }
        Log.e("DIAG", "TOTAL=${all.size} byStatus=$byStatus isCancelledBoolTrue=${isCancelledTrue.size}")
        // duplicate uids: a CONFIRMED and a CANCELLED copy of same uid?
        val byUid = all.groupBy { it.uid }
        val dup = byUid.filter { (u, lst) -> lst.size > 1 }
        Log.e("DIAG", "DUP_UID_COUNT=${dup.size}")
        for ((u, lst) in dup) {
            val stats = lst.map { "${it.status}/${if (it.isCancelled) "canc" else "live"}/${it.recurrenceId ?: "M"}" }
            Log.e("DIAG", "DUP uid=${u.take(24)} -> $stats")
        }
        // (B) wrong-day: weekly masters, compare BYDAY vs actual July weekdays
        val julStart = java.time.LocalDate.of(2026,7,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val julEnd = java.time.LocalDate.of(2026,8,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val expanded = calendarRepo.getEventsInRangeUnified(listOf(aid), julStart, julEnd).first()
        val masters = all.filter { it.isMaster() && it.recurrenceRule?.freq == com.unifiedcomms.data.model.RecurrenceFrequency.WEEKLY }
        // model DayOfWeek (SU=0..SA=6) -> java DayOfWeek name
        fun mday(d: com.unifiedcomms.data.model.DayOfWeek): String =
            java.time.DayOfWeek.values()[d.ordinal].name
        for (m in masters) {
            val byDaySet = m.recurrenceRule?.byDay?.map { mday(it.day) }?.toSet() ?: emptySet()
            val occDays = expanded.filter { it.uid == m.uid && it.recurrenceId != null }.map { ev ->
                val zid = java.time.ZoneId.of(com.unifiedcomms.data.model.TimeZoneUtil.normalize(ev.startAt.timeZone) ?: "UTC")
                java.time.Instant.ofEpochMilli(ev.startAt.toInstant(com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(ev.startAt.timeZone)).toEpochMilliseconds()).atZone(zid).toLocalDateTime().dayOfWeek.name
            }.toSet()
            val masterYear = (m.startAt.dateTime?.year ?: (m.startAt.date?.year ?: 0))
            // For empty BYDAY, expected weekday = master's own start weekday.
            val expected = if (byDaySet.isNotEmpty()) byDaySet else setOf(occDays.firstOrNull() ?: "")
            val wrong = if (byDaySet.isNotEmpty()) (occDays - byDaySet) else emptySet()
            if (wrong.isNotEmpty()) {
                Log.e("DIAG", "WRONGDAY title='${m.title.take(30)}' startYear=$masterYear byDay=$byDaySet occDays=$occDays WRONG=$wrong")
            }
        }
        // stale masters: master start year < 2025 (old re-imports the user wants gone)
        val stale = masters.filter { (it.startAt.dateTime?.year ?: (it.startAt.date?.year ?: 0)) < 2025 }
        Log.e("DIAG", "STALE_OLD_MASTERS=${stale.size} of ${masters.size} weekly masters; recentSince2025=${masters.size - stale.size}")
        // Ground truth: expand the Guitar TH master directly and print actual occurrence weekdays
        val guitar = all.find { it.title.contains("Guitar") && it.isMaster() && it.recurrenceRule?.byDay?.any { it.day.name == "TH" } == true }
        if (guitar != null) {
            Log.e("DIAG", "GUITAR_MASTER start=${guitar.startAt.dateTime} tz=${guitar.startAt.timeZone} byDay=${guitar.recurrenceRule?.byDay}")
            val occs = com.unifiedcomms.sync.RecurrenceExpander.expand(guitar, julStart, julEnd)
            occs.sortedBy { it.startAt.toInstant().toEpochMilliseconds() }.take(6).forEach { ev ->
                val zid = java.time.ZoneId.of(com.unifiedcomms.data.model.TimeZoneUtil.normalize(ev.startAt.timeZone) ?: "UTC")
                val ldt = java.time.Instant.ofEpochMilli(ev.startAt.toInstant(com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(ev.startAt.timeZone)).toEpochMilliseconds()).atZone(zid).toLocalDateTime()
                Log.e("DIAG", "GUITAR_OCC ${ldt} (${ldt.dayOfWeek}) recId=${ev.recurrenceId}")
            }
        }
        // Ground truth: print repo 'expanded' occurrences for the Guitar TH master (uid 458a)
        val gUid = all.find { it.title.contains("Guitar") && it.isMaster() && it.recurrenceRule?.byDay?.any { it.day.name == "TH" } == true }?.uid
        if (gUid != null) {
            Log.e("DIAG", "REPO_EXPANDED_GUITAR uid=$gUid")
            expanded.filter { it.uid == gUid }.sortedBy { it.startAt.toInstant().toEpochMilliseconds() }.take(6).forEach { ev ->
                val zid = java.time.ZoneId.of(com.unifiedcomms.data.model.TimeZoneUtil.normalize(ev.startAt.timeZone) ?: "UTC")
                val ldt = java.time.Instant.ofEpochMilli(ev.startAt.toInstant(com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(ev.startAt.timeZone)).toEpochMilliseconds()).atZone(zid).toLocalDateTime()
                Log.e("DIAG", "REPO_GUITAR_OCC ${ldt} (${ldt.dayOfWeek})")
            }
        }
        Log.e("DIAG", "WRONGDAY_SCAN_DONE")
        // Replicate UI dedupEvents on the expanded set, report Guitar days + totals
        val filt = expanded.filter { !it.uid.endsWith("@google.com", true) }
        val byTitle = filt.groupBy { "${it.title.trim().lowercase()}|${it.accountId}" }
        val deduped = mutableListOf<com.unifiedcomms.data.model.CalendarEvent>()
        for ((_, group) in byTitle) {
            val masters = group.filter { it.recurrenceId == null }
            if (masters.isEmpty()) { deduped.addAll(group); continue }
            val newestUid = masters.maxByOrNull { it.startAt.toInstant(kotlinx.datetime.TimeZone.of(it.startAt.timeZone)).toEpochMilliseconds() }?.uid ?: masters.first().uid
            for (ev in group) if (ev.uid == newestUid) deduped.add(ev)
        }
        val guitarDays = deduped.filter { it.title.contains("Guitar") }.map { ev ->
            val zid = java.time.ZoneId.of(com.unifiedcomms.data.model.TimeZoneUtil.normalize(ev.startAt.timeZone) ?: "UTC")
            java.time.Instant.ofEpochMilli(ev.startAt.toInstant(com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(ev.startAt.timeZone)).toEpochMilliseconds()).atZone(zid).toLocalDateTime().dayOfWeek.name
        }.distinct().sorted()
        Log.e("DIAG", "DEDUP_TOTAL before=${filt.size} after=${deduped.size}")
        Log.e("DIAG", "DEDUP_GUITAR_DAYS=$guitarDays")
        // Debug: print surviving Guitar Practice events' uid + day
        deduped.filter { it.title.contains("Guitar Practice") }.forEach { ev ->
            val zid = java.time.ZoneId.of(com.unifiedcomms.data.model.TimeZoneUtil.normalize(ev.startAt.timeZone) ?: "UTC")
            val d = java.time.Instant.ofEpochMilli(ev.startAt.toInstant(com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(ev.startAt.timeZone)).toEpochMilliseconds()).atZone(zid).toLocalDateTime().dayOfWeek.name
            Log.e("DIAG", "SURVIVED_GP uid=${ev.uid.take(12)} recId=${ev.recurrenceId?.take(8)} day=$d")
        }
        // Why aren't the two Guitar masters collapsing? Dump their keys.
        all.filter { it.title.contains("Guitar") && it.isMaster() }.forEach { m ->
            Log.e("DIAG", "GUITAR_MASTER title='${m.title}' acct=${m.accountId.take(8)} cal=${m.calendarId.take(20)} uid=${m.uid.take(12)} byDay=${m.recurrenceRule?.byDay} start=${m.startAt.dateTime}")
        }
    }
}
