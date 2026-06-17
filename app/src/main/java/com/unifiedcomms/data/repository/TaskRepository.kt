package com.unifiedcomms.data.repository

import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import com.unifiedcomms.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface TaskRepository {
    suspend fun insert(task: Task): Long
    suspend fun insertAll(tasks: List<Task>): List<Long>
    suspend fun update(task: Task): Int
    suspend fun updateAll(tasks: List<Task>): Int
    suspend fun delete(task: Task): Int
    suspend fun deleteById(id: String): Int
    suspend fun getById(id: String): Task?
    suspend fun getByUid(uid: String, accountId: String): Task?
    fun getByList(accountId: String, listId: String): Flow<List<Task>>
    fun getActiveByAccount(accountId: String, completedStatus: TaskStatus): Flow<List<Task>>
    fun getActiveUnified(accountIds: List<String>, completedStatus: TaskStatus): Flow<List<Task>>
    fun getByStatus(accountId: String, status: TaskStatus): Flow<List<Task>>
    fun getDueOnDate(accountId: String, date: Long): Flow<List<Task>>
    fun getDueOnDateUnified(accountIds: List<String>, date: Long): Flow<List<Task>>
    fun getOverdue(accountId: String, now: Long, completedStatus: TaskStatus): Flow<List<Task>>
    fun getOverdueUnified(accountIds: List<String>, now: Long, completedStatus: TaskStatus): Flow<List<Task>>
    fun getUpcoming(accountId: String, now: Long, end: Long, completedStatus: TaskStatus, limit: Int): Flow<List<Task>>
    fun getUpcomingUnified(accountIds: List<String>, now: Long, end: Long, completedStatus: TaskStatus, limit: Int): Flow<List<Task>>
    fun getSubtasks(parentId: String): Flow<List<Task>>
    fun searchTasks(query: String, accountIds: List<String>, limit: Int): Flow<List<Task>>
    suspend fun getNeedingSync(accountId: String): List<Task>
    suspend fun getLocalOnly(accountId: String): List<Task>
    suspend fun markCompleted(id: String, completed: Boolean)
    suspend fun markSynced(id: String): Int
    suspend fun markAllNeedingSync(accountId: String): Int
    suspend fun updatePosition(id: String, position: Int): Int

    // Task Lists
    suspend fun insertList(list: TaskList): Long
    suspend fun insertLists(lists: List<TaskList>): List<Long>
    suspend fun updateList(list: TaskList): Int
    suspend fun deleteList(list: TaskList): Int
    suspend fun getListById(id: String): TaskList?
    fun getListsByAccount(accountId: String): Flow<List<TaskList>>
    suspend fun getListByServerId(accountId: String, serverId: String): TaskList?
    suspend fun updateTaskCount(id: String, count: Int): Int
    suspend fun updateCompletedCount(id: String, count: Int): Int
}