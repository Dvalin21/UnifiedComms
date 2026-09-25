package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.AttendeeRole
import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventAttendee
import com.unifiedcomms.data.model.EventColor
import com.unifiedcomms.data.model.RecurrenceException
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskPriority
import com.unifiedcomms.data.model.TaskStatus
import kotlinx.datetime.Instant
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ICalParser {
    private const val TAG = "ICalParser"

    data class ParseResult(val events: List<CalendarEvent>, val tasks: List<Task>)

    fun parse(ical: String, accountId: String, calendarPath: String, resourceHref: String, defaultColor: String = ""): ParseResult {
        val tasks = mutableListOf<Task>()
        val lines = unfoldLines(ical)
        val veventBlocks = mutableListOf<List<String>>()
        val vtodoBlocks = mutableListOf<List<String>>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.equals("BEGIN:VEVENT", true) -> {
                    val (component, next) = extractComponent(lines, i, "VEVENT")
                    veventBlocks.add(component)
                    i = next
                }
                line.equals("BEGIN:VTODO", true) -> {
                    val (component, next) = extractComponent(lines, i, "VTODO")
                    vtodoBlocks.add(component)
                    i = next
                }
                else -> i++
            }
        }
        vtodoBlocks.mapNotNull { parseVTodo(it, accountId, calendarPath, resourceHref) }.toCollection(tasks)
        // ponytail: group sibling VEVENTs by UID so a RECURRENCE-ID override (same UID,
        // no RRULE) is attached to its master as a RecurrenceException instead of being
        // emitted as a phantom standalone event. EXDATEs are folded into the master too.
        val events = mergeVEvents(veventBlocks, accountId, calendarPath, resourceHref, defaultColor)
        return ParseResult(events, tasks)
    }

    private fun mergeVEvents(
        blocks: List<List<String>>,
        accountId: String,
        calendarPath: String,
        resourceHref: String,
        defaultColor: String = ""
    ): List<CalendarEvent> {
        val parsed = blocks.mapNotNull { parseVEventWithRecurrenceId(it, accountId, calendarPath, resourceHref, defaultColor) }
        val byUid = parsed.groupBy { it.event.uid }
        val out = mutableListOf<CalendarEvent>()
        for ((_, group) in byUid) {
            val master = group.firstOrNull { it.event.isMaster() } ?: group.first()
            val overrides = group.filter { it !== master }
            if (overrides.isEmpty()) {
                out.add(master.event)
                continue
            }
            val exceptions = master.event.recurrenceExceptions.toMutableList()
            for (o in overrides) {
                val rid = o.recurrenceId ?: continue
                exceptions.add(
                    RecurrenceException(
                        originalDate = rid,
                        exceptionEvent = o.event,
                        isDeleted = o.event.status == com.unifiedcomms.data.model.EventStatus.CANCELLED
                    )
                )
            }
            out.add(master.event.copy(recurrenceExceptions = exceptions))
        }
        return out
    }

    private data class ParsedVEvent(val event: CalendarEvent, val recurrenceId: Instant?)

    private fun parseVEventWithRecurrenceId(
        lines: List<String>,
        accountId: String,
        calendarPath: String,
        resourceHref: String,
        defaultColor: String = ""
    ): ParsedVEvent? {
        val event = parseVEvent(lines, accountId, calendarPath, resourceHref, defaultColor) ?: return null
        val map = parseProperties(lines)
        // ponytail: RECURRENCE-ID carries params (TZID); look it up by prefix, not exact key.
        val ridEntry = map.entries.firstOrNull { it.key.startsWith("RECURRENCE-ID") }
        val rid = ridEntry?.let { parseRecurrenceId(it.value, map) }
        return ParsedVEvent(event, rid)
    }

    private fun parseVEvent(lines: List<String>, accountId: String, calendarPath: String, resourceHref: String, defaultColor: String = ""): CalendarEvent? {
        val map = parseProperties(lines)
        return try {
            val uid = map["UID"] ?: return null
            val summary = map["SUMMARY"] ?: "(No title)"
            val dtstartEntry = map.entries.firstOrNull { it.key.startsWith("DTSTART") }
            val dtendEntry = map.entries.firstOrNull { it.key.startsWith("DTEND") } ?: map.entries.firstOrNull { it.key.startsWith("DURATION") }
            val startTzId = tzIdFromKey(dtstartEntry?.key)
            val allDay = dtstartEntry?.key?.contains(";VALUE=DATE", true) == true && dtstartEntry.key.contains("DATE", true) && !dtstartEntry.key.contains("DATE-TIME", true)
                || (dtstartEntry?.key?.substringBefore(';') == "DTSTART" && dtstartEntry.value.length == 8 && dtstartEntry.value.all { it.isDigit() })
            val startMs = runCatching { parseDateTime(dtstartEntry!!.key, dtstartEntry.value, startTzId) }.getOrNull() ?: 0L
            val endMs = when {
                dtendEntry != null && dtendEntry.key.startsWith("DTEND") ->
                    parseDateTime(dtendEntry.key, dtendEntry.value, tzIdFromKey(dtendEntry.key))
                dtendEntry != null && dtendEntry.key.startsWith("DURATION") ->
                    parseDurationMs(dtendEntry.value)?.let { startMs + it }
                        ?: if (allDay) startMs + 86_400_000L else startMs + 3_600_000L
                allDay -> startMs + 86_400_000L
                else -> startMs + 3_600_000L
            }
            val status = when ((map["STATUS"] ?: "").uppercase()) {
                "CANCELLED" -> com.unifiedcomms.data.model.EventStatus.CANCELLED
                "TENTATIVE" -> com.unifiedcomms.data.model.EventStatus.TENTATIVE
                else -> com.unifiedcomms.data.model.EventStatus.CONFIRMED
            }
            val colorHex = (map["X-APPLE-COLOR"] ?: map["COLOR"] ?: map["X-MICROSOFT-CALENDAR-CALCOLOR"])
                ?.trim()?.takeIf { it.isNotEmpty() }.orEmpty()
            val organizer = parseOrganizer(lines)
            val attendees = parseAttendees(lines)
            // ponytail: EXDATE lines delete specific occurrences (server-side cancellation /
            // reschedule). Merge them into recurrenceExceptions as deleted overrides.
            val exdates = extractExdates(lines)

            CalendarEvent(
                accountId = accountId,
                calendarId = calendarPath,
                uid = uid,
                title = summary,
                description = map["DESCRIPTION"] ?: "",
                location = map["LOCATION"] ?: "",
                startAt = com.unifiedcomms.data.model.EventDateTime.fromInstant(
                    kotlinx.datetime.Instant.fromEpochMilliseconds(startMs),
                    startTzId?.let { com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(it) } ?: kotlinx.datetime.TimeZone.currentSystemDefault(),
                    allDay
                ),
                endAt = com.unifiedcomms.data.model.EventDateTime.fromInstant(
                    kotlinx.datetime.Instant.fromEpochMilliseconds(endMs),
                    startTzId?.let { com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(it) } ?: kotlinx.datetime.TimeZone.currentSystemDefault(),
                    allDay
                ),
                timezone = startTzId?.let { com.unifiedcomms.data.model.TimeZoneUtil.normalize(it) } ?: ZoneId.systemDefault().id,
                recurrenceRule = map["RRULE"]?.let { com.unifiedcomms.data.model.RecurrenceRule.parse(it) },
                recurrenceExceptions = exdates,
                color = if (colorHex.isNotBlank()) {
                    EventColor(colorHex, if (isLightColor(colorHex)) "#000000" else "#FFFFFF", isExplicit = true)
                } else if (defaultColor.isNotBlank()) {
                    EventColor(defaultColor, if (isLightColor(defaultColor)) "#000000" else "#FFFFFF", isExplicit = false)
                } else {
                    EventColor.Default().copy(isExplicit = false)
                },
                organizer = organizer,
                attendees = attendees,
                serverHref = resourceHref,
                status = status,
                isCancelled = status == com.unifiedcomms.data.model.EventStatus.CANCELLED
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse VEVENT; calendarPath=$calendarPath accountId=$accountId", t)
            null
        }
    }

    private fun parseVTodo(lines: List<String>, accountId: String, calendarPath: String, resourceHref: String): Task? {
        val map = parseProperties(lines)
        return try {
            val uid = map["UID"] ?: return null
            val summary = unescapeText(map["SUMMARY"] ?: "(No title)")
            val status = when ((map["STATUS"] ?: "").uppercase()) {
                "COMPLETED" -> TaskStatus.COMPLETED
                "IN-PROCESS" -> TaskStatus.IN_PROCESS
                "CANCELLED" -> TaskStatus.CANCELLED
                else -> TaskStatus.NEEDS_ACTION
            }
            val due = map.entries.firstOrNull { it.key.startsWith("DUE") }?.let { parseTaskDateTime(it.key, it.value) }
            val completed = map.entries.firstOrNull { it.key.startsWith("COMPLETED") }?.let { parseTaskDateTime(it.key, it.value) }
            val priorityNum = map["PRIORITY"]?.toIntOrNull() ?: 0
            val priority = when {
                priorityNum == 1 -> TaskPriority.URGENT
                priorityNum in 2..4 -> TaskPriority.HIGH
                priorityNum == 5 -> TaskPriority.MEDIUM
                priorityNum in 6..9 -> TaskPriority.LOW
                else -> TaskPriority.NONE
            }
            val categories = map["CATEGORIES"]
                ?.split(",")
                ?.map { unescapeText(it) }
                ?.filter { it.isNotBlank() }
                .orEmpty()

            Task(
                accountId = accountId,
                listId = calendarPath,
                uid = uid,
                title = summary,
                description = map["DESCRIPTION"]?.let(::unescapeText),
                location = map["LOCATION"]?.let(::unescapeText),
                status = status,
                priority = priority,
                dueAt = due,
                completedAt = completed,
                percentComplete = map["PERCENT-COMPLETE"]?.toIntOrNull()
                    ?: if (status == TaskStatus.COMPLETED) 100 else 0,
                categories = categories,
                serverHref = resourceHref,
                // TaskSyncEngine overwrites this with the real ETag after listing.
                etag = resourceHref
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse VTODO; calendarPath=$calendarPath accountId=$accountId", t)
            null
        }
    }

    private fun parseTaskDateTime(key: String, value: String): com.unifiedcomms.data.model.TaskDateTime? {
        val clean = value.trim()
        val tz = tzIdFromKey(key) ?: if (clean.endsWith("Z", ignoreCase = true)) "UTC" else ZoneId.systemDefault().id
        val propertyName = key.substringBefore(';').uppercase()
        val dateOnly = (propertyName == "DUE" || propertyName == "COMPLETED") &&
            key.contains("VALUE=DATE", ignoreCase = true) && !key.contains("DATE-TIME", ignoreCase = true)
        return if (dateOnly) {
            val date = java.time.LocalDate.parse(clean.take(8), DateTimeFormatter.BASIC_ISO_DATE)
            com.unifiedcomms.data.model.TaskDateTime(
                date = kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth),
                timeZone = tz,
                hasTime = false
            )
        } else {
            val millis = parseDateTime(key, clean, tz)
            com.unifiedcomms.data.model.TaskDateTime.fromInstant(
                kotlinx.datetime.Instant.fromEpochMilliseconds(millis),
                com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(tz),
                hasTime = true
            )
        }
    }

    private fun unescapeText(value: String): String {
        val out = StringBuilder(value.length)
        var escaped = false
        for (char in value) {
            if (escaped) {
                out.append(
                    when (char) {
                        'n', 'N' -> '\n'
                        else -> char
                    }
                )
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else {
                out.append(char)
            }
        }
        if (escaped) out.append('\\')
        return out.toString()
    }

    private fun parseProperties(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            // ponytail: uppercase only the property name (up to ';'), NOT the params
            // (e.g. TZID). Uppercasing the whole key broke RECURRENCE-ID;TZID=... lookups.
            val rawKey = line.substring(0, idx).trim()
            val semi = rawKey.indexOf(';')
            val key = if (semi >= 0) rawKey.substring(0, semi).uppercase() + rawKey.substring(semi) else rawKey.uppercase()
            val value = line.substring(idx + 1)
            if (key == "UID" || !map.containsKey(key)) map[key] = value
        }
        return map
    }

    private fun parseDateTime(key: String, value: String, tzId: String? = null): Long {
        val clean = value.trim()
        val zone = resolveZoneId(tzId) ?: ZoneId.systemDefault()
        return when {
            clean.endsWith("Z") -> LocalDateTime.parse(clean.replace("Z", ""), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                .atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
            clean.contains("T") -> LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                .atZone(zone).toInstant().toEpochMilli()
            else -> LocalDate.parse(clean, DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }

    // DTSTART;TZID=America/New_York:... -> "America/New_York"
    private fun tzIdFromKey(key: String?): String? {
        if (key == null) return null
        val idx = key.indexOf("TZID=", ignoreCase = true)
        if (idx < 0) return null
        return key.substring(idx + 5).substringBefore(':').trim().takeIf { it.isNotBlank() }
    }

    // ponytail: Java ZoneId.of() is case-sensitive, but servers frequently emit TZIDs in
    // non-canonical case (e.g. "AMERICA/NEW_YORK"). A direct ZoneId.of() miss silently falls
    // back to systemDefault() and corrupts every timestamp in that zone — so resolve
    // case-insensitively against the available zone ids, memoized.
    private val zoneIdCache = mutableMapOf<String, ZoneId?>()
    private fun resolveZoneId(raw: String?): ZoneId? {
        if (raw.isNullOrBlank()) return null
        zoneIdCache[raw]?.let { return it }
        val direct = runCatching { ZoneId.of(raw) }.getOrNull()
        if (direct != null) { zoneIdCache[raw] = direct; return direct }
        val lower = raw.lowercase()
        val matched = ZoneId.getAvailableZoneIds().firstOrNull { it.lowercase() == lower }
        val resolved = matched?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        zoneIdCache[raw] = resolved
        return resolved
    }

    private fun extractEmail(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().let {
            if (it.startsWith("mailto:", ignoreCase = true)) it.substring(7) else it
        }
        return normalized.trim().takeIf { it.contains('@') }
    }

    private fun parseOrganizer(lines: List<String>): EventAttendee? {
        val line = lines.firstOrNull { propertyLine(it)?.first?.let { key ->
            propertyName(key).equals("ORGANIZER", ignoreCase = true)
        } == true } ?: return null
        val (rawKey, rawValue) = propertyLine(line) ?: return null
        val email = extractEmail(rawValue) ?: return null
        return EventAttendee(
            email = email,
            name = parameter(rawKey, "CN")?.let(::unescapeText),
            status = AttendeeStatus.ACCEPTED,
            role = AttendeeRole.ORGANIZER,
            rsvp = false
        )
    }

    private fun parseAttendees(lines: List<String>): List<EventAttendee> = buildList {
        for (line in lines) {
            val (rawKey, rawValue) = propertyLine(line) ?: continue
            if (!propertyName(rawKey).equals("ATTENDEE", ignoreCase = true)) continue
            val email = extractEmail(rawValue) ?: continue
            val role = when (parameter(rawKey, "ROLE")?.uppercase()) {
                "CHAIR" -> AttendeeRole.CHAIR
                "OPT-PARTICIPANT" -> AttendeeRole.OPT_PARTICIPANT
                "NON-PARTICIPANT" -> AttendeeRole.NON_PARTICIPANT
                else -> AttendeeRole.REQ_PARTICIPANT
            }
            val status = when (parameter(rawKey, "PARTSTAT")?.uppercase()) {
                "ACCEPTED" -> AttendeeStatus.ACCEPTED
                "DECLINED" -> AttendeeStatus.DECLINED
                "TENTATIVE" -> AttendeeStatus.TENTATIVE
                "DELEGATED" -> AttendeeStatus.DELEGATED
                "COMPLETED" -> AttendeeStatus.COMPLETED
                "IN-PROCESS" -> AttendeeStatus.IN_PROCESS
                else -> AttendeeStatus.NEEDS_ACTION
            }
            add(
                EventAttendee(
                    email = email,
                    name = parameter(rawKey, "CN")?.let(::unescapeText),
                    status = status,
                    role = role,
                    rsvp = parameter(rawKey, "RSVP")?.equals("TRUE", ignoreCase = true) ?: true
                )
            )
        }
    }

    private fun propertyLine(line: String): Pair<String, String>? {
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        return line.substring(0, idx).trim() to line.substring(idx + 1).trim()
    }

    private fun propertyName(rawKey: String): String = rawKey.substringBefore(';').trim()

    private fun parameter(rawKey: String, name: String): String? = rawKey.split(';')
        .drop(1)
        .firstOrNull { it.substringBefore('=').trim().equals(name, ignoreCase = true) }
        ?.substringAfter('=', "")
        ?.trim()
        ?.removeSurrounding("\"")
        ?.takeIf { it.isNotBlank() }

    private fun isLightColor(hex: String): Boolean {
        val clean = hex.removePrefix("#")
        val color = runCatching { android.graphics.Color.parseColor("#$clean") }.getOrElse { -1 }
        if (color == -1) return true
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return luminance > 0.6
    }

    private fun extractComponent(lines: List<String>, start: Int, name: String): Pair<List<String>, Int> {
        val out = mutableListOf<String>()
        var i = start
        while (i < lines.size) {
            out.add(lines[i])
            if (lines[i].trim().equals("END:$name", true)) return out to (i + 1)
            i++
        }
        return out to i
    }

    // EXDATE may repeat (one line per source) and each value may list comma-separated
    // datetimes. The TZID param (if present on the line) applies to all values on it.
    // RFC: an EXDATE marks a generated occurrence as cancelled — surfaced as a deleted
    // RecurrenceException so the expander can drop the generated instance.
    private fun extractExdates(lines: List<String>): List<RecurrenceException> {
        val out = mutableListOf<RecurrenceException>()
        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim().uppercase()
            if (!key.startsWith("EXDATE")) continue
            val tzId = tzIdFromKey(key)
            for (tok in line.substring(idx + 1).split(',')) {
                val ms = runCatching { parseDateTime(key, tok.trim(), tzId) }.getOrNull() ?: continue
                out.add(RecurrenceException(originalDate = Instant.fromEpochMilliseconds(ms), isDeleted = true))
            }
        }
        return out
    }

    // RECURRENCE-ID:19980101T120000Z  or  RECURRENCE-ID;TZID=America/New_York:19980101T120000
    // The TZID (if any) is needed to resolve the instant correctly — recover it from the prop key.
    private fun parseRecurrenceId(value: String, map: Map<String, String>): Instant? {
        val key = map.entries.firstOrNull { it.key.startsWith("RECURRENCE-ID") }?.key ?: "RECURRENCE-ID:$value"
        val tzId = tzIdFromKey(key)
        val ms = runCatching { parseDateTime(key, value.trim(), tzId) }.getOrNull() ?: return null
        return Instant.fromEpochMilliseconds(ms)
    }

    private fun unfoldLines(text: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        for (raw in text.lines()) {
            when {
                raw.startsWith(" ") || raw.startsWith("\t") -> cur.append(raw.trimStart())
                else -> {
                    if (cur.isNotEmpty()) result.add(cur.toString())
                    cur = StringBuilder(raw)
                }
            }
        }
        if (cur.isNotEmpty()) result.add(cur.toString())
        return result
    }

    // ponytail: RFC 5545 DURATION (e.g. "PT1H", "P1D", "P1W", "P2DT3H30M") -> milliseconds.
    // parseVEvent previously hard-coded +1h when no DTEND was present, silently dropping
    // the real DURATION. Resolve it so event end times are correct.
    private fun parseDurationMs(value: String): Long? {
        val v = value.trim().uppercase()
        if (!v.startsWith("P")) return null
        var totalMs = 0L
        var num = StringBuilder()
        var inTime = false
        for (c in v) {
            when {
                c == 'P' -> {}
                c == 'T' -> inTime = true
                c.isDigit() -> num.append(c)
                c == 'W' -> totalMs += (num.toString().toLongOrNull() ?: 0) * 7 * 86_400_000L
                c == 'D' -> totalMs += (num.toString().toLongOrNull() ?: 0) * 86_400_000L
                c == 'H' -> totalMs += (num.toString().toLongOrNull() ?: 0) * 3_600_000L
                c == 'M' -> totalMs += (num.toString().toLongOrNull() ?: 0) * 60_000L
                c == 'S' -> totalMs += (num.toString().toLongOrNull() ?: 0) * 1_000L
                else -> return null
            }
            if (c in "WDHMS") num = StringBuilder()
        }
        return totalMs.takeIf { it > 0 }
    }
}
