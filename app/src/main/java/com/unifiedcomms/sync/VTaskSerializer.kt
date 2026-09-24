package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.model.TimeZoneUtil
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Serialize a [Task] to a minimal RFC5545 VTODO object.
 *
 * ponytail: only emits fields that [ICalParser.parseVTodo] actually reads back
 * (UID, SUMMARY, DESCRIPTION, STATUS, DUE, PRIORITY, CATEGORIES). No PERCENT-COMPLETE /
 * RRULE / attendees yet — those are read-only display today, so round-tripping them
 * would be speculative. Extend here when the model needs write support for them.
 */
object VTaskSerializer {
    private const val TAG = "VTaskSerializer"

    fun toVtodo(task: Task, uid: String = task.uid.ifBlank { java.util.UUID.randomUUID().toString() }): String {
        val sb = StringBuilder()
        // ponytail: CalDAV requires a full VCALENDAR envelope around the VTODO.
        // A bare BEGIN:VTODO (no VCALENDAR) is rejected by SOGo/mailcow with 404 on
        // PUT. Mirrors the VEventSerializer fix.
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//UnifiedComms//Tasks//EN")
        sb.appendLine("BEGIN:VTODO")
        sb.appendLine("UID:$uid")

        if (task.title.isNotBlank()) sb.appendLine("SUMMARY:${escape(task.title)}")
        task.description?.takeIf { it.isNotBlank() }?.let { sb.appendLine("DESCRIPTION:${escape(it)}") }

        sb.appendLine("STATUS:${when (task.status) {
            TaskStatus.COMPLETED -> "COMPLETED"
            TaskStatus.IN_PROCESS -> "IN-PROCESS"
            TaskStatus.CANCELLED -> "CANCELLED"
            else -> "NEEDS-ACTION"
        }}")

        task.dueAt?.let { due ->
            val normalizedZone = TimeZoneUtil.normalize(due.timeZone) ?: "UTC"
            if (due.hasTime) {
                val zone = runCatching { ZoneId.of(normalizedZone) }.getOrNull() ?: ZoneId.of("UTC")
                val zoned = java.time.Instant.ofEpochMilli(due.toInstant().toEpochMilliseconds()).atZone(zone)
                sb.appendLine("DUE;TZID=$normalizedZone:${zoned.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))}")
            } else {
                val date = due.date ?: due.dateTime?.date
                if (date != null) {
                    sb.appendLine("DUE;VALUE=DATE:${date.toString().replace("-", "")}")
                } else {
                    Unit
                }
            }
        }

        val prio = when (task.priority) {
            com.unifiedcomms.data.model.TaskPriority.URGENT, com.unifiedcomms.data.model.TaskPriority.HIGH -> 1
            com.unifiedcomms.data.model.TaskPriority.MEDIUM -> 5
            com.unifiedcomms.data.model.TaskPriority.LOW -> 9
            else -> 0
        }
        if (prio > 0) sb.appendLine("PRIORITY:$prio")

        if (task.categories.isNotEmpty()) sb.appendLine("CATEGORIES:${task.categories.joinToString(",") { escape(it) }}")

        sb.appendLine("END:VTODO")
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}
