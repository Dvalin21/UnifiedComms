package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskStatus
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
            runCatching {
                val zoned = java.time.Instant.ofEpochMilli(due.toInstant().toEpochMilliseconds()).atZone(ZoneId.of(due.timeZone))
                val fmt = if (due.hasTime) "yyyyMMdd'T'HHmmss" else "yyyyMMdd"
                // ponytail: a DUE carries its wall-clock zone in due.timeZone, so emit it
                // as a LOCAL time with a TZID (not a floating Z). Stamping Z masqueraded
                // the wall-clock as UTC and shifted the due time by the zone offset.
                val zoneSuffix = if (due.hasTime) "" else ""
                sb.appendLine("DUE;TZID=${due.timeZone}:${zoned.format(DateTimeFormatter.ofPattern(fmt + zoneSuffix))}")
            }.onFailure { Log.w(TAG, "bad DUE for ${task.id}", it) }
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
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}
