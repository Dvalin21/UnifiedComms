package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.model.TaskPriority
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VTaskSerializerTest {

    private fun mkTask(uid: String, title: String, status: TaskStatus, prio: TaskPriority, dueMs: Long?): Task {
        return Task(
            id = uid,
            accountId = "a",
            listId = "/tasks/list",
            uid = uid,
            title = title,
            description = "desc for $uid",
            status = status,
            priority = prio,
            dueAt = dueMs?.let { com.unifiedcomms.data.model.TaskDateTime.fromInstant(Instant.fromEpochMilliseconds(it), TimeZone.of("UTC")) },
            categories = listOf("work", "urgent")
        )
    }

    @Test
    fun `serialized VTODO contains required fields`() {
        val vtodo = VTaskSerializer.toVtodo(mkTask("u1", "Buy milk", TaskStatus.NEEDS_ACTION, TaskPriority.HIGH, null))
        assertTrue("has BEGIN/END", vtodo.startsWith("BEGIN:VTODO") && vtodo.trimEnd().endsWith("END:VTODO"))
        assertTrue("has UID", vtodo.contains("UID:u1"))
        assertTrue("has SUMMARY", vtodo.contains("SUMMARY:Buy milk"))
        assertTrue("has STATUS", vtodo.contains("STATUS:NEEDS-ACTION"))
        assertTrue("has PRIORITY", vtodo.contains("PRIORITY:1"))
        assertTrue("has CATEGORIES", vtodo.contains("CATEGORIES:work,urgent"))
        assertTrue("has DESCRIPTION", vtodo.contains("DESCRIPTION:desc for u1"))
    }

    @Test
    fun `status maps correctly`() {
        val completed = VTaskSerializer.toVtodo(mkTask("u2", "Done", TaskStatus.COMPLETED, TaskPriority.NONE, null))
        assertTrue(completed.contains("STATUS:COMPLETED"))
        val inproc = VTaskSerializer.toVtodo(mkTask("u3", "Half", TaskStatus.IN_PROCESS, TaskPriority.MEDIUM, null))
        assertTrue(inproc.contains("STATUS:IN-PROCESS"))
        assertTrue(inproc.contains("PRIORITY:5"))
    }

    @Test
    fun `due serializes with TZID, not floating Z`() {
        // 1_784_092_800_000L == 2026-07-15T05:20:00Z. Serializer emits the wall-clock
        // local time in the task's tz (UTC here) with a TZID=UTC parameter — NOT a
        // trailing Z, which would re-interpret the wall-clock as UTC-floating and is
        // only correct by accident when the zone already is UTC.
        val due = 1_784_092_800_000L
        val vtodo = VTaskSerializer.toVtodo(mkTask("u4", "Timed", TaskStatus.NEEDS_ACTION, TaskPriority.NONE, due))
        assertTrue("DUE carries TZID=UTC", vtodo.contains("DUE;TZID=UTC:20260715T052000"))
        assertTrue("DUE has no floating Z", !vtodo.contains("DUE:20260715T052000Z"))
    }

    @Test
    fun `round-trip through ICalParser keeps title and status`() {
        val task = mkTask("u5", "Round trip", TaskStatus.IN_PROCESS, TaskPriority.LOW, null)
        val vtodo = VTaskSerializer.toVtodo(task, "u5")
        val parsed = ICalParser.parse(vtodo, "a", "/tasks/list", "/tasks/list/u5.ics")
        assertEquals(1, parsed.tasks.size)
        val back = parsed.tasks.first()
        assertEquals("Round trip", back.title)
        assertEquals(TaskStatus.IN_PROCESS, back.status)
        assertEquals("u5", back.uid)
    }

    @Test
    fun `summary escaping of newline and semicolon`() {
        val vtodo = VTaskSerializer.toVtodo(mkTask("u6", "Line1\nLine2; end", TaskStatus.NEEDS_ACTION, TaskPriority.NONE, null))
        assertTrue("newline escaped", vtodo.contains("SUMMARY:Line1\\nLine2\\; end"))
    }
}
