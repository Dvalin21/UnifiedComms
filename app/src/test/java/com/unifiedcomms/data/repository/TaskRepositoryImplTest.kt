package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.TaskDao
import com.unifiedcomms.data.model.Task
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
        val overdue = baseTask(id = "o", title = "Overdue")
        whenever(taskDao.getOverdueUnified(any(), any(), any())).thenReturn(flowOf(listOf(overdue)))
        val result = repo.getOverdueUnified(listOf("a1"), 1000L, TaskStatus.NEEDS_ACTION).first()
        assertEquals(listOf(overdue), result)
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
