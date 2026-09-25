package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.TaskDao
import com.unifiedcomms.data.db.dao.TaskListDao
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import com.unifiedcomms.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val listDao: TaskListDao
) : TaskRepository {
    override suspend fun insert(task: Task): Long = taskDao.insert(task)

    override suspend fun insertAll(tasks: List<Task>): List<Long> = taskDao.insertAll(tasks)

    override suspend fun update(task: Task): Int = taskDao.update(task)

    override suspend fun updateAll(tasks: List<Task>): Int = taskDao.updateAll(tasks)

    override suspend fun delete(task: Task): Int = taskDao.delete(task)

    override suspend fun deleteById(id: String): Int = taskDao.deleteById(id)

    override suspend fun getById(id: String): Task? = taskDao.getById(id)

    override suspend fun getByUid(uid: String, accountId: String): Task? = taskDao.getByUid(uid, accountId)

    override suspend fun getByUidAndList(uid: String, accountId: String, listId: String): Task? =
        taskDao.getByUidAndList(uid, accountId, listId)

    override fun getByList(accountId: String, listId: String): Flow<List<Task>> = taskDao.getByList(accountId, listId)

    override fun getActiveByAccount(accountId: String, completedStatus: TaskStatus): Flow<List<Task>> =
        taskDao.getActiveByAccount(accountId, completedStatus)

    override fun getActiveUnified(accountIds: List<String>, completedStatus: TaskStatus): Flow<List<Task>> =
        taskDao.getActiveUnified(accountIds, completedStatus)

    override fun getAllUnified(accountIds: List<String>): Flow<List<Task>> =
        taskDao.getAllUnified(accountIds)

    override fun getByStatus(accountId: String, status: TaskStatus): Flow<List<Task>> = taskDao.getByStatus(accountId, status)

    override fun getDueOnDate(accountId: String, date: Long): Flow<List<Task>> =
        taskDao.getActiveByAccount(accountId, com.unifiedcomms.data.model.TaskStatus.COMPLETED).map { list ->
            val target = localDate(date)
            list.filter { localDueDate(it) == target }
        }

    override fun getDueOnDateUnified(accountIds: List<String>, date: Long): Flow<List<Task>> =
        taskDao.getActiveUnified(accountIds, com.unifiedcomms.data.model.TaskStatus.COMPLETED).map { list ->
            val target = localDate(date)
            list.filter { localDueDate(it) == target }
        }

    override fun getOverdue(accountId: String, now: Long, completedStatus: TaskStatus): Flow<List<Task>> =
        taskDao.getActiveByAccount(accountId, completedStatus).map { list ->
            val today = localDate(now)
            list.filter { task ->
                val dueDate = localDueDate(task)
                dueDate != null && dueDate < today
            }
        }

    override fun getOverdueUnified(accountIds: List<String>, now: Long, completedStatus: TaskStatus): Flow<List<Task>> =
        taskDao.getActiveUnified(accountIds, completedStatus).map { list ->
            val today = localDate(now)
            list.filter { task -> localDueDate(task)?.let { it < today } == true }
        }

    override fun getUpcoming(accountId: String, now: Long, end: Long, completedStatus: TaskStatus, limit: Int): Flow<List<Task>> =
        taskDao.getActiveByAccount(accountId, completedStatus).map { list ->
            upcoming(list, now, end, limit)
        }

    override fun getUpcomingUnified(accountIds: List<String>, now: Long, end: Long, completedStatus: TaskStatus, limit: Int): Flow<List<Task>> =
        taskDao.getActiveUnified(accountIds, completedStatus).map { list ->
            upcoming(list, now, end, limit)
        }

    override fun getSubtasks(parentId: String): Flow<List<Task>> = taskDao.getSubtasks(parentId)

    override fun searchTasks(query: String, accountIds: List<String>, limit: Int): Flow<List<Task>> =
        taskDao.searchTasks("%$query%", accountIds, limit)

    override suspend fun getNeedingSync(accountId: String): List<Task> = taskDao.getNeedingSync(accountId)

    override suspend fun getLocalOnly(accountId: String): List<Task> = taskDao.getLocalOnly(accountId)

    override suspend fun markCompleted(id: String, completed: Boolean) = taskDao.markCompleted(id, completed)

    override suspend fun markSynced(id: String): Int = taskDao.markSynced(id)

    override suspend fun markAllNeedingSync(accountId: String): Int = taskDao.markAllNeedingSync(accountId)

    override suspend fun updatePosition(id: String, position: Int): Int = taskDao.updatePosition(id, position)

    // Task Lists
    override suspend fun insertList(list: TaskList): Long = listDao.insert(list)

    override suspend fun insertLists(lists: List<TaskList>): List<Long> = listDao.insertAll(lists)

    override suspend fun updateList(list: TaskList): Int = listDao.update(list)

    override suspend fun deleteList(list: TaskList): Int = listDao.delete(list)

    override suspend fun getListById(id: String): TaskList? = listDao.getById(id)

    override fun getListsByAccount(accountId: String): Flow<List<TaskList>> = listDao.getByAccount(accountId)

    override suspend fun getListByServerId(accountId: String, serverId: String): TaskList? =
        listDao.getByServerId(accountId, serverId)

    override suspend fun updateTaskCount(id: String, count: Int): Int = listDao.updateTaskCount(id, count)

    override suspend fun updateCompletedCount(id: String, count: Int): Int = listDao.updateCompletedCount(id, count)

    private fun localDate(epochMs: Long): java.time.LocalDate =
        java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    private fun localDueDate(task: Task): java.time.LocalDate? {
        val due = task.dueAt?.takeUnless { it.isEmpty() } ?: return null
        if (!due.hasTime) {
            val date = due.date ?: due.dateTime?.date ?: return null
            return java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
        }
        val zone = runCatching {
            java.time.ZoneId.of(com.unifiedcomms.data.model.TimeZoneUtil.normalize(due.timeZone) ?: "UTC")
        }.getOrDefault(java.time.ZoneId.systemDefault())
        return java.time.Instant.ofEpochMilli(due.toInstant().toEpochMilliseconds()).atZone(zone).toLocalDate()
    }

    private fun upcoming(tasks: List<Task>, now: Long, end: Long, limit: Int): List<Task> {
        val startDate = localDate(now)
        val endDate = localDate(end)
        return tasks.filter { task ->
            val due = task.dueAt?.takeUnless { it.isEmpty() } ?: return@filter false
            if (due.hasTime) {
                val instant = due.toInstant().toEpochMilliseconds()
                instant in now..end
            } else {
                val date = localDueDate(task) ?: return@filter false
                date in startDate..endDate
            }
        }.sortedBy { it.dueAt?.toInstant()?.toEpochMilliseconds() ?: Long.MAX_VALUE }.take(limit)
    }
}
