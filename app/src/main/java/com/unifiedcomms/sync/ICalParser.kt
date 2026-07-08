package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventColor
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskPriority
import com.unifiedcomms.data.model.TaskStatus
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ICalParser {
    private const val TAG = "ICalParser"

    data class ParseResult(val events: List<CalendarEvent>, val tasks: List<Task>)

    fun parse(ical: String, accountId: String, calendarPath: String, etag: String): ParseResult {
        val events = mutableListOf<CalendarEvent>()
        val tasks = mutableListOf<Task>()
        val lines = unfoldLines(ical)
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.equals("BEGIN:VEVENT", true) -> {
                    val (component, next) = extractComponent(lines, i, "VEVENT")
                    val parsed = parseVEvent(component, accountId, calendarPath, etag)
                    if (parsed != null) events.add(parsed)
                    else {
                        val uid = component.firstOrNull { it.trim().uppercase().startsWith("UID:") }?.substringAfter(":") ?: "unknown"
                        Log.w(TAG, "VEVENT parse failed; uid=$uid calendarPath=$calendarPath accountId=$accountId")
                    }
                    i = next
                }
                line.equals("BEGIN:VTODO", true) -> {
                    val (component, next) = extractComponent(lines, i, "VTODO")
                    val parsed = parseVTodo(component, accountId, calendarPath, etag)
                    if (parsed != null) tasks.add(parsed)
                    i = next
                }
                else -> i++
            }
        }
        return ParseResult(events, tasks)
    }

    private fun parseVEvent(lines: List<String>, accountId: String, calendarPath: String, etag: String): CalendarEvent? {
        val map = parseProperties(lines)
        return try {
            val uid = map["UID"] ?: return null
            val summary = map["SUMMARY"] ?: "(No title)"
            val dtstartEntry = map.entries.firstOrNull { it.key.startsWith("DTSTART") }
            val dtendEntry = map.entries.firstOrNull { it.key.startsWith("DTEND") } ?: map.entries.firstOrNull { it.key.startsWith("DURATION") }
            val startTzId = tzIdFromKey(dtstartEntry?.key)
            val allDay = dtstartEntry?.key?.contains(";VALUE=DATE", true) == true && dtstartEntry.key.contains("DATE", true) && !dtstartEntry.key.contains("DATE-TIME", true)
            val startMs = runCatching { parseDateTime(dtstartEntry!!.key, dtstartEntry.value, startTzId) }.getOrNull() ?: 0L
            val endMs = if (dtendEntry != null && dtendEntry.key.startsWith("DTEND")) parseDateTime(dtendEntry.key, dtendEntry.value, tzIdFromKey(dtendEntry.key))
            else if (allDay) startMs + 86_400_000L else startMs + 3_600_000L
            val status = when ((map["STATUS"] ?: "").uppercase()) {
                "CANCELLED" -> com.unifiedcomms.data.model.EventStatus.CANCELLED
                "TENTATIVE" -> com.unifiedcomms.data.model.EventStatus.TENTATIVE
                else -> com.unifiedcomms.data.model.EventStatus.CONFIRMED
            }
            val colorHex = map["X-APPLE-COLOR"] ?: map["COLOR"] ?: map["X-MICROSOFT-CALENDAR-CALCOLOR"] ?: ""
            val organizerEmail = extractEmail(map.entries.firstOrNull { it.key.startsWith("ORGANIZER") }?.value)

            CalendarEvent(
                accountId = accountId,
                calendarId = calendarPath,
                uid = uid,
                title = summary,
                description = map["DESCRIPTION"] ?: "",
                location = map["LOCATION"] ?: "",
                startAt = com.unifiedcomms.data.model.EventDateTime.fromInstant(
                    kotlinx.datetime.Instant.fromEpochMilliseconds(startMs),
                    startTzId?.let { kotlinx.datetime.TimeZone.of(it) } ?: kotlinx.datetime.TimeZone.currentSystemDefault(),
                    allDay
                ),
                endAt = com.unifiedcomms.data.model.EventDateTime.fromInstant(
                    kotlinx.datetime.Instant.fromEpochMilliseconds(endMs),
                    startTzId?.let { kotlinx.datetime.TimeZone.of(it) } ?: kotlinx.datetime.TimeZone.currentSystemDefault(),
                    allDay
                ),
                timezone = startTzId ?: ZoneId.systemDefault().id,
                recurrenceRule = map["RRULE"]?.let { com.unifiedcomms.data.model.RecurrenceRule.parse(it) },
                color = if (colorHex.isNotBlank()) EventColor(colorHex, if (isLightColor(colorHex)) "#000000" else "#FFFFFF") else EventColor.Default(),
                organizer = organizerEmail?.let { com.unifiedcomms.data.model.EventAttendee(email = it) },
                etag = etag,
                status = status
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse VEVENT; calendarPath=$calendarPath accountId=$accountId", t)
            null
        }
    }

    private fun parseVTodo(lines: List<String>, accountId: String, calendarPath: String, etag: String): Task? {
        val map = parseProperties(lines)
        return try {
            val uid = map["UID"] ?: return null
            val summary = map["SUMMARY"] ?: "(No title)"
            val status = when ((map["STATUS"] ?: "").uppercase()) {
                "COMPLETED" -> TaskStatus.COMPLETED
                "IN-PROCESS" -> TaskStatus.IN_PROCESS
                "CANCELLED" -> TaskStatus.CANCELLED
                else -> TaskStatus.NEEDS_ACTION
            }
            val dueMs = map.entries.firstOrNull { it.key.startsWith("DUE") }?.let { parseDateTime(it.key, it.value) }
            val priorityNum = map["PRIORITY"]?.toIntOrNull() ?: 0
            val priority = when {
                priorityNum in 1..4 -> TaskPriority.HIGH
                priorityNum == 5 -> TaskPriority.MEDIUM
                priorityNum in 6..9 -> TaskPriority.LOW
                else -> TaskPriority.NONE
            }

            Task(
                accountId = accountId,
                listId = calendarPath,
                uid = uid,
                title = summary,
                description = map["DESCRIPTION"] ?: "",
                status = status,
                priority = priority,
                dueAt = dueMs?.let { com.unifiedcomms.data.model.TaskDateTime.fromInstant(kotlinx.datetime.Instant.fromEpochMilliseconds(it)) },
                etag = etag
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse VTODO; calendarPath=$calendarPath accountId=$accountId", t)
            null
        }
    }

    private fun parseProperties(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim().uppercase()
            val value = line.substring(idx + 1)
            if (key == "UID" || !map.containsKey(key)) map[key] = value
        }
        return map
    }

    private fun parseDateTime(key: String, value: String, tzId: String? = null): Long {
        val clean = value.trim()
        val zone = tzId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
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

    private fun extractEmail(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return value.removePrefix("mailto:").trim().ifBlank { null }
    }

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
}
