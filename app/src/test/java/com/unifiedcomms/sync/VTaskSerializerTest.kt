package com.unifiedcomms.sync

import com.unifiedcomms.data.db.converters.TaskDateTimeConverter
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.model.TaskPriority
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue("has VCALENDAR envelope", vtodo.startsWith("BEGIN:VCALENDAR") && vtodo.trimEnd().endsWith("END:VCALENDAR"))
        assertTrue("has VTODO envelope", vtodo.contains("BEGIN:VTODO") && vtodo.contains("END:VTODO"))
        assertTrue("has DTSTAMP", vtodo.contains("DTSTAMP:"))
        assertTrue("has UID", vtodo.contains("UID:u1"))
        assertTrue("has SUMMARY", vtodo.contains("SUMMARY:Buy milk"))
        assertTrue("has STATUS", vtodo.contains("STATUS:NEEDS-ACTION"))
        assertTrue("has PRIORITY", vtodo.contains("PRIORITY:3"))
        assertTrue("has CATEGORIES", vtodo.contains("CATEGORIES:work,urgent"))
        assertTrue("has DESCRIPTION", vtodo.contains("DESCRIPTION:desc for u1"))
    }

    fun `status maps correctly`() {
        val completedTask = mkTask("u2", "Done", TaskStatus.COMPLETED, TaskPriority.NONE, null).copy(
            completedAt = com.unifiedcomms.data.model.TaskDateTime(
                date = kotlinx.datetime.LocalDate(2026, 9, 23),
                hasTime = false
            ),
            percentComplete = 100
        )
        val completed = VTaskSerializer.toVtodo(completedTask)
        assertTrue(completed.contains("STATUS:COMPLETED"))
        assertTrue(completed.contains("COMPLETED;VALUE=DATE:20260923"))
        assertTrue(completed.contains("PERCENT-COMPLETE:100"))
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
    fun `date-only due uses RFC DATE value`() {
        val task = mkTask("u-date", "All day", TaskStatus.NEEDS_ACTION, TaskPriority.NONE, null).copy(
            dueAt = com.unifiedcomms.data.model.TaskDateTime(
                date = kotlinx.datetime.LocalDate(2026, 9, 15),
                hasTime = false
            )
        )

        val vtodo = VTaskSerializer.toVtodo(task)

        assertTrue(vtodo.contains("DUE;VALUE=DATE:20260915"))
        assertFalse(vtodo.contains("DUE;TZID="))
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
        assertEquals("/tasks/list/u5.ics", back.serverHref)
    }

    @Test
    fun `null task dates stay null`() {
        assertEquals(null, TaskDateTimeConverter().toDateTime(null))
        assertEquals(null, TaskDateTimeConverter().toDateTime("not-json"))
    }

    @Test
    fun `empty due value is not serialized as a current timestamp`() {
        val task = mkTask("u-empty", "No due", TaskStatus.NEEDS_ACTION, TaskPriority.NONE, null)
            .copy(dueAt = com.unifiedcomms.data.model.TaskDateTime())
        val vtodo = VTaskSerializer.toVtodo(task)
        assertFalse(vtodo.contains("DUE"))
    }

    @Test
    fun `date-only and TZID due values survive parsing`() {
        val dateOnly = ICalParser.parse(
            "BEGIN:VCALENDAR\nBEGIN:VTODO\nUID:date-only\nSUMMARY:Date\nDUE;VALUE=DATE:20260915\nEND:VTODO\nEND:VCALENDAR",
            "a",
            "/tasks/list",
            "/tasks/list/date-only.ics"
        ).tasks.single()
        assertFalse(dateOnly.dueAt!!.hasTime)
        assertEquals(2026, dateOnly.dueAt!!.date!!.year)

        val timed = ICalParser.parse(
            "BEGIN:VCALENDAR\nBEGIN:VTODO\nUID:timed\nSUMMARY:Timed\nDUE;TZID=America/New_York:20260915T090000\nEND:VTODO\nEND:VCALENDAR",
            "a",
            "/tasks/list",
            "/tasks/list/timed.ics"
        ).tasks.single()
        assertEquals("America/New_York", timed.dueAt!!.timeZone)
        assertTrue(timed.dueAt!!.hasTime)
    }

    @Test
    fun `urgent priority stays urgent`() {
        val vtodo = VTaskSerializer.toVtodo(mkTask("urgent", "Urgent", TaskStatus.NEEDS_ACTION, TaskPriority.URGENT, null))
        assertTrue(vtodo.contains("PRIORITY:1"))
        val parsed = ICalParser.parse(vtodo, "a", "/tasks/list", "/tasks/list/urgent.ics").tasks.single()
        assertEquals(TaskPriority.URGENT, parsed.priority)
    }

    @Test
    fun `server href wins over derived filename`() {
        val task = mkTask("u7", "Stable", TaskStatus.NEEDS_ACTION, TaskPriority.NONE, null)
            .copy(serverHref = "/tasks/list/server-renamed.ics")
        assertEquals("/tasks/list/server-renamed.ics", VTaskSerializer.hrefFor(task))
    }

    @Test
    fun `summary escaping of newline and semicolon`() {
        val vtodo = VTaskSerializer.toVtodo(mkTask("u6", "Line1\nLine2; end", TaskStatus.NEEDS_ACTION, TaskPriority.NONE, null))
        assertTrue("newline escaped", vtodo.contains("SUMMARY:Line1\\nLine2\\; end"))
    }
}
