package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TaskSyncEngine {
    val syncProgress: StateFlow<Map<String, SyncProgress>>
    suspend fun syncAccount(account: Account): SyncResult
    suspend fun syncTaskList(account: Account, taskList: TaskList): SyncResult
    suspend fun fetchTask(account: Account, listId: String, uid: String): Task?
    suspend fun createTask(account: Account, task: Task): CreateResult
    suspend fun updateTask(account: Account, task: Task): SyncResult
    suspend fun deleteTask(account: Account, listId: String, uid: String): SyncResult
    suspend fun completeTask(account: Account, task: Task): SyncResult
    fun observeSyncProgress(accountId: String): Flow<SyncProgress>
    suspend fun testConnection(account: Account): ConnectionTestResult
    suspend fun getTaskLists(account: Account): List<TaskList>
}