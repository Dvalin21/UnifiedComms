package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventColor
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.model.EventStatus
import com.unifiedcomms.data.model.TimeZoneUtil
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Serialize a [CalendarEvent] to a minimal RFC5545 VEVENT.
 *
 * ponytail: only emits fields the app actually edits/reads back — UID, DTSTAMP,
 * DTSTART/DTEND (with TZID, never a floating Z), SUMMARY, DESCRIPTION, LOCATION,
 * STATUS, RRULE, and the server-side RECURRENCE-ID exception uses. Attendees /
 * reminders / attachments / conference are display-only today; round-tripping them
 * would be speculative. Extend here when the model needs write support for them.
 *
 * DTSTART/DTEND carry their wall-clock zone in [EventDateTime.timeZone]; emit it as a
 * LOCAL time with a TZID (the same fix that corrected VTaskSerializer). Stamping Z
 * masquerades wall-clock as UTC and shifts the event by the zone offset.
 */
object VEventSerializer {
    private const val TAG = "VEventSerializer"

    fun toVevent(
        event: CalendarEvent,
        uid: String = event.uid.ifBlank { java.util.UUID.randomUUID().toString() }
    ): String {
        val sb = StringBuilder()
        // ponytail: CalDAV requires a full VCALENDAR envelope around the VEVENT.
        // A bare BEGIN:VEVENT (no VCALENDAR) is rejected by SOGo/mailcow with 404
        // on PUT. This matches what ICalParser reads back and what VTaskSerializer
        // must also emit for VTODO.
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//UnifiedComms//Calendar//EN")
        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("UID:$uid")
        sb.appendLine("DTSTAMP:${nowUtcStamp()}")

        sb.appendLine(formatDateTime("DTSTART", event.startAt))
        sb.appendLine(formatDateTime("DTEND", event.endAt))

        if (event.title.isNotBlank()) sb.appendLine("SUMMARY:${escape(event.title)}")
        event.description?.takeIf { it.isNotBlank() }?.let { sb.appendLine("DESCRIPTION:${escape(it)}") }
        event.location?.takeIf { it.isNotBlank() }?.let { sb.appendLine("LOCATION:${escape(it)}") }

        sb.appendLine("STATUS:${when (event.status) {
            EventStatus.CANCELLED -> "CANCELLED"
            EventStatus.TENTATIVE -> "TENTATIVE"
            else -> "CONFIRMED"
        }}")

        event.recurrenceRule?.let { sb.appendLine("RRULE:${it.toRfc5545()}") }

        // ponytail: emit RECURRENCE-ID for exception instances so the server can store
        // them as overrides of the master. Without this, editing one occurrence of a
        // recurring event is silently lost on round-trip (ICalParser reads RECURRENCE-ID
        // back but the serializer never wrote it).
        event.recurrenceId?.let { rid ->
            sb.appendLine("RECURRENCE-ID:${rid}")
        }

        // ponytail: emit EXDATE entries so server-side cancellations of specific occurrences
        // survive a round-trip. ICalParser reads EXDATE back into recurrenceExceptions.
        event.recurrenceExceptions.filter { it.isDeleted }.forEach { ex ->
            sb.appendLine("EXDATE:${ex.originalDate.toEpochMilliseconds()}")
        }

        // Preserve the user-chosen event color so it survives a round-trip through
        // the server. COLOR is RFC 7986; X-APPLE-COLOR is what SOGo/Apple clients read.
        if (event.color != EventColor.Default() && event.color.background.isNotBlank()) {
            sb.appendLine("COLOR:${event.color.background}")
            sb.appendLine("X-APPLE-COLOR:${event.color.background}")
        }

        sb.appendLine("END:VEVENT")
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    /**
     * Build the collection-relative item href for a VEVENT. The download-side dedup
     * keys the local cache by pathOf(hrefFor(event)), so this must be the same
     * normalized path the server returns for the object: `<calendarId>/<uid>.ics`.
     * [CalendarEvent.calendarId] is the collection path (e.g. `/caldav/.../work/`).
     */
    fun hrefFor(event: CalendarEvent): String {
        val cal = event.calendarId.trimEnd('/')
        val uid = event.uid.ifBlank { java.util.UUID.randomUUID().toString() }
        return "$cal/$uid.ics"
    }

    private fun formatDateTime(name: String, dt: EventDateTime): String {
        val zoneId = TimeZoneUtil.normalize(dt.timeZone)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
        return runCatching {
            if (dt.isAllDay && dt.date != null) {
                val d = dt.date!!
                "$name;VALUE=DATE:${String.format("%04d%02d%02d", d.year, d.monthNumber, d.dayOfMonth)}"
            } else if (dt.dateTime != null) {
                val zoned = java.time.LocalDateTime.parse(dt.dateTime.toString()).atZone(zoneId)
                val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                "$name;TZID=${TimeZoneUtil.normalize(dt.timeZone)}:${zoned.format(fmt)}"
            } else {
                "$name;VALUE=DATE:${nowUtcDate()}"
            }
        }.onFailure { Log.w(TAG, "bad $name for ${dt.timeZone}", it) }
            .getOrDefault("$name;VALUE=DATE:${nowUtcDate()}")
    }

    private fun nowUtcStamp(): String =
        java.time.Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

    private fun nowUtcDate(): String =
        java.time.Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}
