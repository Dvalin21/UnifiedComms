package com.unifiedcomms.sync

import com.unifiedcomms.data.model.EventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ICalParserRecurrenceExceptionTest {

    // Master weekly event with one EXDATE (server-side cancellation of 2026-07-13).
    private val exdateIcal = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//test//EN
        BEGIN:VEVENT
        UID:standup
        SUMMARY:Standup
        DTSTART;TZID=America/New_York:20260706T090000
        DTEND;TZID=America/New_York:20260706T091500
        RRULE:FREQ=WEEKLY;BYDAY=MO
        EXDATE;TZID=America/New_York:20260713T090000
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun `EXDATE parsed into recurrenceExceptions as deleted`() {
        val res = ICalParser.parse(exdateIcal, "a", "/cal/c", "/cal/c/standup.ics")
        assertEquals(1, res.events.size)
        val ev = res.events.first()
        assertEquals(1, ev.recurrenceExceptions.size)
        val ex = ev.recurrenceExceptions.first()
        assertTrue(ex.isDeleted)
        // 2026-07-13 09:00 NY (EDT, UTC-4) == 13:00 UTC
        assertEquals(1_783_947_600_000L, ex.originalDate.toEpochMilliseconds())
    }

    // Master + a RECURRENCE-ID override moving the 2026-07-13 instance to 2026-07-14 14:00.
    private val overrideIcal = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//test//EN
        BEGIN:VEVENT
        UID:standup
        SUMMARY:Standup
        DTSTART;TZID=America/New_York:20260706T090000
        DTEND;TZID=America/New_York:20260706T091500
        RRULE:FREQ=WEEKLY;BYDAY=MO
        END:VEVENT
        BEGIN:VEVENT
        UID:standup
        RECURRENCE-ID;TZID=America/New_York:20260713T090000
        SUMMARY:Standup (moved)
        DTSTART;TZID=America/New_York:20260714T140000
        DTEND;TZID=America/New_York:20260714T141500
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun `RECURRENCE-ID override merged as non-deleted exception`() {
        val res = ICalParser.parse(overrideIcal, "a", "/cal/c", "/cal/c/standup.ics")
        assertEquals(1, res.events.size)
        val ev = res.events.first()
        assertEquals(1, ev.recurrenceExceptions.size)
        val ex = ev.recurrenceExceptions.first()
        assertFalse(ex.isDeleted)
        assertEquals(1_783_947_600_000L, ex.originalDate.toEpochMilliseconds())
        assertTrue(ex.exceptionEvent?.title?.contains("moved") == true)
        // moved instance start 2026-07-14 14:00 NY (EDT) == 18:00 UTC
        assertEquals(1_784_052_000_000L, ex.exceptionEvent?.startAt?.toInstant()?.toEpochMilliseconds())
    }

    // Cancelled override (STATUS:CANCELLED) — original occurrence must be suppressed.
    private val cancelledIcal = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//test//EN
        BEGIN:VEVENT
        UID:standup
        SUMMARY:Standup
        DTSTART;TZID=America/New_York:20260706T090000
        DTEND;TZID=America/New_York:20260706T091500
        RRULE:FREQ=WEEKLY;BYDAY=MO
        END:VEVENT
        BEGIN:VEVENT
        UID:standup
        RECURRENCE-ID;TZID=America/New_York:20260713T090000
        SUMMARY:Standup
        DTSTART;TZID=America/New_York:20260713T090000
        DTEND;TZID=America/New_York:20260713T091500
        STATUS:CANCELLED
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun `cancelled RECURRENCE-ID override is flagged deleted`() {
        val res = ICalParser.parse(cancelledIcal, "a", "/cal/c", "/cal/c/standup.ics")
        assertEquals(1, res.events.size)
        val ex = res.events.first().recurrenceExceptions.first()
        assertTrue(ex.isDeleted)
        assertEquals(EventStatus.CANCELLED, ex.exceptionEvent?.status)
    }
}
