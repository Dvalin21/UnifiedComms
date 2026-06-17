package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.TaskDao
import com.unifiedcomms.data.db.dao.TaskListDao
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import com.unifiedcomms.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow
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

    override fun getByList(accountId: String, listId: String): Flow<List<Task>> = taskDao.getByList(accountId, listId)

    override fun getActiveByAccount(accountId: String, completedStatus: TaskStatus): Flow<List<Task>> =
        taskDao.getActiveByAccount(accountId, completedStatus)

    override fun getActiveUnified(accountIds: List<String>, completedStatus: TaskStatus): Flow<List<Task>> =
        taskDao.getActiveUnified(accountIds, completedStatus)

    override fun getByStatus(accountId: String, status: TaskStatus): Flow<List<Task>> = taskDao.getByStatus(accountId, status)

    override fun getDueOnDate(accountId: String, date: Long): Flow<List<Task>> = taskDao.getDueOnDate(accountId, date)

    override fun getDueOnDateUnified(accountIds: List<String>, date: Long): Flow<List<Task>> =
        taskDao.getDueOnDateUnified(accountIds, date)

    override fun getOverdue(accountId: String, now: Long, completedStatus: TaskStatus): Flow<List<Task>> =
        taskDao.getOverdue(accountId, now, completedStatus)

    override fun getOverdueUnified(accountIds: List<String>, now: Long, completedStatus: TaskStatus): Flow<List<Task>> =
        taskDao.getOverdueUnified(accountIds, now, completedStatus)

    override fun getUpcoming(accountId: String, now: Long, end: Long, completedStatus: TaskStatus, limit: Int): Flow<List<Task>> =
        taskDao.getUpcoming(accountId, now, end, completedStatus, limit)

    override fun getUpcomingUnified(accountIds: List<String>, now: Long, end: Long, completedStatus: TaskStatus, limit: Int): Flow<List<Task>> =
        taskDao.getUpcomingUnified(accountIds, now, end, completedStatus, limit)

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
}