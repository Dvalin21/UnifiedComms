package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.TaskDao
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskDateTime
import com.unifiedcomms.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TaskRepositoryImplTest {

    private lateinit var taskDao: TaskDao
    private lateinit var repo: TaskRepositoryImpl

    @Before
    fun setUp() {
        taskDao = mock()
        repo = TaskRepositoryImpl(taskDao, mock())
    }

    private fun baseTask(
        id: String = "t1",
        accountId: String = "a1",
        listId: String = "l1",
        uid: String = "u1",
        title: String = "Test Task",
        status: TaskStatus = TaskStatus.NEEDS_ACTION
    ): Task {
        return Task(
            id = id,
            accountId = accountId,
            listId = listId,
            uid = uid,
            title = title,
            status = status
        )
    }

    @Test
    fun `getByStatus delegates to dao`() = runTest {
        val task = baseTask()
        whenever(taskDao.getByStatus(any(), any())).thenReturn(flowOf(listOf(task)))
        val result = repo.getByStatus("a1", TaskStatus.NEEDS_ACTION).first()
        assertEquals(listOf(task), result)
    }

    @Test
    fun `getOverdueUnified delegates to dao`() = runTest {
        val overdue = baseTask(id = "o", title = "Overdue", status = TaskStatus.NEEDS_ACTION)
            .copy(dueAt = TaskDateTime.fromInstant(kotlinx.datetime.Instant.fromEpochMilliseconds(800L), hasTime = true))
        whenever(taskDao.getActiveUnified(any(), any())).thenReturn(flowOf(listOf(overdue)))
        whenever(taskDao.getByStatus(any(), any())).thenReturn(flowOf(emptyList()))
        val result = repo.getOverdueUnified(listOf("a1"), System.currentTimeMillis(), TaskStatus.NEEDS_ACTION).first()
        assertEquals(listOf(overdue), result)
    }

    @Test
    fun `getDueOnDate filters active tasks by local date`() = runTest {
        val localDate = java.time.LocalDate.now()
        val task = baseTask().copy(
            dueAt = TaskDateTime(
                date = kotlinx.datetime.LocalDate(localDate.year, localDate.monthValue, localDate.dayOfMonth),
                hasTime = false
            )
        )
        whenever(taskDao.getActiveByAccount(any(), any())).thenReturn(flowOf(listOf(task)))
        assertEquals(listOf(task), repo.getDueOnDate("a1", System.currentTimeMillis()).first())
        verify(taskDao).getActiveByAccount("a1", TaskStatus.COMPLETED)
    }

    @Test
    fun `getUpcoming honors exact timed bounds`() = runTest {
        val before = baseTask(id = "before")
            .withDueAt(TaskDateTime.fromInstant(kotlinx.datetime.Instant.fromEpochMilliseconds(900L), hasTime = true))
        val inside = baseTask(id = "inside")
            .withDueAt(TaskDateTime.fromInstant(kotlinx.datetime.Instant.fromEpochMilliseconds(1_100L), hasTime = true))
        val after = baseTask(id = "after")
            .withDueAt(TaskDateTime.fromInstant(kotlinx.datetime.Instant.fromEpochMilliseconds(1_300L), hasTime = true))
        whenever(taskDao.getActiveByAccount(any(), any())).thenReturn(flowOf(listOf(before, inside, after)))

        val result = repo.getUpcoming("a1", 1_000L, 1_200L, TaskStatus.NEEDS_ACTION, 10).first()

        assertEquals(listOf(inside), result)
    }

    @Test
    fun `searchTasks delegates to dao`() = runTest {
        val task = baseTask(title = "Search Target")
        whenever(taskDao.searchTasks(any(), any(), any())).thenReturn(flowOf(listOf(task)))
        val result = repo.searchTasks("Search", listOf("a1"), 10).first()
        assertEquals(listOf(task), result)
    }

    @Test
    fun `markCompleted delegates to dao`() = runTest {
        repo.markCompleted("t1", true)
        verify(taskDao).markCompleted("t1", true)
    }
}
