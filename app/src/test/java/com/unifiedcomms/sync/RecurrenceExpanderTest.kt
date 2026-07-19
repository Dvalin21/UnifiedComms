package com.unifiedcomms.sync

import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.DayOfWeek
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.model.RecurrenceDay
import com.unifiedcomms.data.model.RecurrenceException
import com.unifiedcomms.data.model.RecurrenceFrequency
import com.unifiedcomms.data.model.RecurrenceRule
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class RecurrenceExpanderTest {

    private fun master(uid: String, startMs: Long, durationMs: Long, rule: RecurrenceRule?, tz: String = "UTC"): CalendarEvent {
        val zone = TimeZone.of(tz)
        return CalendarEvent(
            id = uid,
            accountId = "a",
            calendarId = "c",
            uid = uid,
            title = "R",
            startAt = EventDateTime.fromInstant(Instant.fromEpochMilliseconds(startMs), zone),
            endAt = EventDateTime.fromInstant(Instant.fromEpochMilliseconds(startMs + durationMs), zone),
            recurrenceRule = rule
        )
    }

    private fun epochMs(y: Int, mo: Int, d: Int, h: Int, mi: Int, tz: String): Long =
        java.time.ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of(tz)).toInstant().toEpochMilli()

    private fun hourInZone(ms: Long, tz: String): Int =
        java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.of(tz)).hour

    @Test
    fun `non-recurring returns single`() {
        val m = master("x", epochMs(2026, 7, 6, 9, 0, "UTC"), 3_600_000, null)
        assertEquals(1, RecurrenceExpander.expand(m, 0, Long.MAX_VALUE).size)
    }

    @Test
    fun `weekly BYDAY Monday expands to one per week in window`() {
        // 2026-07-06 is a Monday. Window 2026-07-01..2026-08-01 -> Mondays 7/6, 7/13, 7/20, 7/27.
        val start = epochMs(2026, 7, 6, 9, 0, "America/New_York")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.WEEKLY, byDay = listOf(RecurrenceDay(DayOfWeek.MO)))
        val m = master("w", start, 3_600_000, rule, "America/New_York")
        val out = RecurrenceExpander.expand(m, epochMs(2026, 7, 1, 0, 0, "America/New_York"), epochMs(2026, 8, 1, 0, 0, "America/New_York"))
        assertEquals(4, out.size)
        assertEquals(start, out.first().startAt.toInstant().toEpochMilliseconds())
        // wall-clock time preserved across the DST fall-back (Nov) is checked separately; here all 09:00
        out.forEach { assertEquals(9, hourInZone(it.startAt.toInstant().toEpochMilliseconds(), "America/New_York")) }
    }

    @Test
    fun `weekly preserves wall-clock time across DST fall-back`() {
        // Weekly Sun 09:00 NY spanning 2026-11-01 (DST ends 2am). Oct 25 EDT(UTC-4); Nov 1 EST(UTC-5). Wall clock stays 09:00.
        val start = epochMs(2026, 10, 25, 9, 0, "America/New_York")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.WEEKLY, byDay = listOf(RecurrenceDay(DayOfWeek.SU)))
        val m = master("dst", start, 3_600_000, rule, "America/New_York")
        val out = RecurrenceExpander.expand(m, start, epochMs(2026, 11, 9, 0, 0, "America/New_York"))
        assertEquals(3, out.size) // 10/25, 11/1, 11/8
        out.forEach { assertEquals(9, hourInZone(it.startAt.toInstant().toEpochMilliseconds(), "America/New_York")) }
        // 10/25 09:00 EDT = 13:00 UTC ; 11/1 09:00 EST = 14:00 UTC (one hour later in UTC, same wall clock)
        assertEquals(13 * 3600_000L, epochMs(2026, 10, 25, 9, 0, "America/New_York") % 86_400_000L)
        assertEquals(14 * 3600_000L, (out[1].startAt.toInstant().toEpochMilliseconds()) % 86_400_000L)
    }

    @Test
    fun `daily with interval 2 every other day`() {
        val start = epochMs(2026, 7, 1, 8, 0, "UTC")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.DAILY, interval = 2)
        val m = master("d", start, 3_600_000, rule, "UTC")
        val out = RecurrenceExpander.expand(m, start, epochMs(2026, 7, 9, 0, 0, "UTC"))
        // 7/1, 7/3, 7/5, 7/7 = 4
        assertEquals(4, out.size)
        assertEquals(2 * 86_400_000L, out[1].startAt.toInstant().toEpochMilliseconds() - out[0].startAt.toInstant().toEpochMilliseconds())
    }

    @Test
    fun `COUNT caps total occurrences including master`() {
        val start = epochMs(2026, 7, 1, 8, 0, "UTC")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.DAILY, count = 3)
        val m = master("c", start, 3_600_000, rule, "UTC")
        val out = RecurrenceExpander.expand(m, 0, Long.MAX_VALUE)
        assertEquals(3, out.size)
    }

    @Test
    fun `UNTIL excludes occurrences after boundary`() {
        val start = epochMs(2026, 7, 1, 8, 0, "UTC")
        // UNTIL 2026-07-04T00:00:00Z -> only 7/1, 7/2, 7/3 within window up to 7/10
        val until = Instant.fromEpochMilliseconds(epochMs(2026, 7, 4, 0, 0, "UTC"))
        val rule = RecurrenceRule(freq = RecurrenceFrequency.DAILY, until = until)
        val m = master("u", start, 3_600_000, rule, "UTC")
        val out = RecurrenceExpander.expand(m, 0, epochMs(2026, 7, 10, 0, 0, "UTC"))
        assertEquals(3, out.size)
    }

    @Test
    fun `monthly BYMONTHDAY lands on the 15th`() {
        val start = epochMs(2026, 1, 15, 10, 0, "UTC")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.MONTHLY, byMonthDay = listOf(15))
        val m = master("m", start, 3_600_000, rule, "UTC")
        val out = RecurrenceExpander.expand(m, 0, epochMs(2026, 4, 1, 0, 0, "UTC"))
        assertEquals(3, out.size) // Jan 15, Feb 15, Mar 15
        out.forEach {
            val d = java.time.Instant.ofEpochMilli(it.startAt.toInstant().toEpochMilliseconds()).atZone(ZoneId.of("UTC")).dayOfMonth
            assertEquals(15, d)
        }
    }

    @Test
    fun `yearly occurs once per year`() {
        val start = epochMs(2026, 12, 25, 18, 0, "UTC")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.YEARLY)
        val m = master("y", start, 3_600_000, rule, "UTC")
        val out = RecurrenceExpander.expand(m, 0, epochMs(2029, 1, 1, 0, 0, "UTC"))
        assertEquals(3, out.size) // 2026, 2027, 2028
    }

    @Test
    fun `master outside window yields no occurrences`() {
        val start = epochMs(2030, 1, 1, 9, 0, "UTC")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.DAILY)
        val m = master("o", start, 3_600_000, rule, "UTC")
        assertEquals(0, RecurrenceExpander.expand(m, epochMs(2026, 1, 1, 0, 0, "UTC"), epochMs(2026, 2, 1, 0, 0, "UTC")).size)
    }

    @Test
    fun `generated instances carry recurrenceId and are flagged instances`() {
        val start = epochMs(2026, 7, 1, 9, 0, "UTC")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.DAILY, count = 2)
        val m = master("r", start, 3_600_000, rule, "UTC")
        val out = RecurrenceExpander.expand(m, 0, Long.MAX_VALUE)
        val master = out.first()
        val inst = out[1]
        assertEquals(null, master.recurrenceId)
        assertEquals("r", inst.recurrenceId)
        assertEquals(true, inst.isInstance())
        assertEquals(null, inst.recurrenceRule)
    }

    @Test
    fun `EXDATE deletes the matching occurrence`() {
        // Daily 09:00 UTC from 2026-07-01; EXDATE the 2026-07-03 occurrence.
        val start = epochMs(2026, 7, 1, 9, 0, "UTC")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.DAILY)
        val exMs = epochMs(2026, 7, 3, 9, 0, "UTC")  // 1783069200000
        val m = master("x", start, 3_600_000, rule, "UTC")
            .copy(recurrenceExceptions = listOf(RecurrenceException(originalDate = Instant.fromEpochMilliseconds(exMs), isDeleted = true)))
        val out = RecurrenceExpander.expand(m, start, epochMs(2026, 7, 6, 0, 0, "UTC"))
        // 7/1, 7/2, (7/3 deleted), 7/4, 7/5 = 4 occurrences
        assertEquals(4, out.size)
        val starts = out.map { it.startAt.toInstant().toEpochMilliseconds() }
        assertEquals(false, starts.contains(exMs))
    }

    @Test
    fun `RECURRENCE-ID override replaces the original occurrence`() {
        // Daily 09:00 UTC from 2026-07-01; the 7/3 instance is moved to 7/3 15:00.
        val start = epochMs(2026, 7, 1, 9, 0, "UTC")
        val rule = RecurrenceRule(freq = RecurrenceFrequency.DAILY)
        val origMs = epochMs(2026, 7, 3, 9, 0, "UTC")   // 1783069200000
        val movedMs = epochMs(2026, 7, 3, 15, 0, "UTC")  // 1783090800000
        val moved = CalendarEvent(
            id = "x#override", accountId = "a", calendarId = "c", uid = "x",
            title = "Moved",
            startAt = EventDateTime.fromInstant(Instant.fromEpochMilliseconds(movedMs), TimeZone.of("UTC")),
            endAt = EventDateTime.fromInstant(Instant.fromEpochMilliseconds(movedMs + 3_600_000), TimeZone.of("UTC")),
            recurrenceRule = null
        )
        val m = master("x", start, 3_600_000, rule, "UTC")
            .copy(recurrenceExceptions = listOf(RecurrenceException(originalDate = Instant.fromEpochMilliseconds(origMs), exceptionEvent = moved)))
        val out = RecurrenceExpander.expand(m, start, epochMs(2026, 7, 6, 0, 0, "UTC"))
        // 7/1, 7/2, (7/3 orig suppressed, 7/3 moved added), 7/4, 7/5 = 5 occurrences
        assertEquals(5, out.size)
        val starts = out.map { it.startAt.toInstant().toEpochMilliseconds() }
        assertEquals(false, starts.contains(origMs))   // original slot suppressed
        assertEquals(true, starts.contains(movedMs))     // moved slot present
    }

    @Test
    fun `monthly BYDAY multi-weekday emits every listed weekday`() {
        // Regression: BYDAY=MO,WE,FR must yield one occurrence per listed weekday each month,
        // not only the first. Master 2026-01-05 (Monday). Window Jan..Mar 2026 (no Feb 30 etc).
        val start = epochMs(2026, 1, 5, 9, 0, "UTC") // Monday
        val rule = RecurrenceRule(
            freq = RecurrenceFrequency.MONTHLY,
            byDay = listOf(
                RecurrenceDay(DayOfWeek.MO),
                RecurrenceDay(DayOfWeek.WE),
                RecurrenceDay(DayOfWeek.FR)
            )
        )
        val m = master("multi", start, 3_600_000, rule, "UTC")
        // Jan: MO 5, WE 7, FR 9 ; WE 14, FR 16 ; WE 21, FR 23 ; WE 28, FR 30
        // (4 Mondays: 5,12,19,26 ; 4 Wednesdays: 7,14,21,28 ; 4 Fridays: 9,16,23,30) = 12 in Jan
        val out = RecurrenceExpander.expand(m, start, epochMs(2026, 2, 1, 0, 0, "UTC"))
        assertEquals(12, out.size)
        // every occurrence is a Monday, Wednesday, or Friday
        out.forEach {
            val dow = java.time.Instant.ofEpochMilli(it.startAt.toInstant().toEpochMilliseconds()).atZone(ZoneId.of("UTC")).dayOfWeek
            assertTrue(dow == java.time.DayOfWeek.MONDAY || dow == java.time.DayOfWeek.WEDNESDAY || dow == java.time.DayOfWeek.FRIDAY)
        }
    }
}
