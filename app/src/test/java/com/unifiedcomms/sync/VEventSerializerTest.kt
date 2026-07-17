package com.unifiedcomms.sync

import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.model.EventStatus
import junit.framework.TestCase.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class VEventSerializerTest {

    private fun timedEvent(uid: String, tz: String): CalendarEvent {
        val start = EventDateTime(dateTime = LocalDateTime.parse("2026-07-20T09:00"), timeZone = tz, isAllDay = false)
        val end = EventDateTime(dateTime = LocalDateTime.parse("2026-07-20T10:00"), timeZone = tz, isAllDay = false)
        return CalendarEvent(
            id = UUID.randomUUID().toString(),
            accountId = "acc1",
            calendarId = "/caldav/user/cal-work/",
            uid = uid,
            title = "Standup; with, team",
            description = "Daily\n sync",
            location = "Room 5",
            startAt = start,
            endAt = end,
            status = EventStatus.CONFIRMED
        )
    }

    @Test
    fun serializesUidSummaryAndTimes() {
        val ical = VEventSerializer.toVevent(timedEvent("evt-1", "America/New_York"))
        assertTrue("must be a VEVENT", ical.startsWith("BEGIN:VEVENT"))
        assertTrue(ical.contains("UID:evt-1"))
        assertTrue(ical.contains("SUMMARY:Standup\\; with\\, team"))
        assertTrue(ical.contains("DESCRIPTION:Daily\\n sync"))
        assertTrue(ical.contains("LOCATION:Room 5"))
        assertTrue(ical.contains("DTSTART;TZID=America/New_York:20260720T090000"))
        assertTrue(ical.contains("DTEND;TZID=America/New_York:20260720T100000"))
        assertTrue("must close VEVENT", ical.trimEnd().endsWith("END:VEVENT"))
    }

    @Test
    fun normalizesNonCanonicalTzCase() {
        // ponytail: server emits uppercase TZID; serializer must emit canonical case,
        // not a floating Z. This is the exact bug class fixed in ICalParser parse.
        val ical = VEventSerializer.toVevent(timedEvent("evt-2", "AMERICA/NEW_YORK"))
        assertTrue(ical.contains("DTSTART;TZID=America/New_York:20260720T090000"))
        assertFalse("must not stamp Z (would shift by zone offset)", ical.contains("T090000Z"))
    }

    @Test
    fun hrefMatchesDownloadConvention() {
        val href = VEventSerializer.hrefFor(timedEvent("evt-3", "UTC"))
        assertEquals("/caldav/user/cal-work/evt-3.ics", href)
    }

    @Test
    fun allDayEmitsValueDate() {
        val start = EventDateTime(date = LocalDate.parse("2026-07-25"), isAllDay = true, timeZone = "America/New_York")
        val end = EventDateTime(date = LocalDate.parse("2026-07-25"), isAllDay = true, timeZone = "America/New_York")
        val ev = timedEvent("evt-4", "America/New_York").copy(startAt = start, endAt = end)
        val ical = VEventSerializer.toVevent(ev)
        assertTrue(ical.contains("DTSTART;VALUE=DATE:20260725"))
    }
}
