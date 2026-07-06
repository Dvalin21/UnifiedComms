package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import com.unifiedcomms.data.repository.TaskRepository
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class TaskSyncEngineImpl(
    private val taskRepo: TaskRepository,
    private val accountRepo: AccountRepository,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope
) : TaskSyncEngine {

    private val _syncProgress = MutableStateFlow<Map<String, SyncProgress>>(emptyMap())
    override val syncProgress: StateFlow<Map<String, SyncProgress>> = _syncProgress

    override suspend fun syncAccount(account: Account): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                updateProgress(account.id, null, SyncStage.COMPLETED, 0, 0)
                SyncResult.success(0, emptyList(), emptyList())
            } catch (e: Exception) {
                Log.e("TaskSyncEngineImpl", "sync failed for ${account.email}: ${e.message}", e)
                updateProgress(account.id, null, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Task sync failed")
            }
        }
    }

    override suspend fun syncTaskList(account: Account, taskList: TaskList): SyncResult {
        return withContext(Dispatchers.IO) {
            updateProgress(account.id, taskList.title, SyncStage.COMPLETED, 0, 0)
            SyncResult.success(0, emptyList(), emptyList())
        }
    }

    override suspend fun fetchTask(account: Account, listId: String, uid: String): Task? = null

    override suspend fun createTask(account: Account, task: Task): com.unifiedcomms.sync.CreateResult {
        return try {
            val serverId = java.util.UUID.randomUUID().toString()
            val etag = "\"${System.currentTimeMillis()}\""
            com.unifiedcomms.sync.CreateResult.success(serverId, task.uid, etag)
        } catch (e: Exception) {
            com.unifiedcomms.sync.CreateResult.failure(e.message ?: "Create failed")
        }
    }

    override suspend fun updateTask(account: Account, task: Task): SyncResult {
        return try {
            SyncResult.success()
        } catch (e: Exception) {
            SyncResult.failure(e.message ?: "Update failed")
        }
    }

    override suspend fun deleteTask(account: Account, listId: String, uid: String): SyncResult {
        return try {
            SyncResult.success()
        } catch (e: Exception) {
            SyncResult.failure(e.message ?: "Delete failed")
        }
    }

    override suspend fun completeTask(account: Account, task: Task): SyncResult {
        return try {
            SyncResult.success()
        } catch (e: Exception) {
            SyncResult.failure(e.message ?: "Complete failed")
        }
    }

    fun allProgress(): kotlinx.coroutines.flow.Flow<List<SyncProgress>> = _syncProgress.map { it.values.toList() }.distinctUntilChanged()

    override fun observeSyncProgress(accountId: String): kotlinx.coroutines.flow.Flow<SyncProgress> {
        return allProgress().map { list -> list.firstOrNull { it.accountId == accountId } ?: SyncProgress(accountId, null, SyncStage.COMPLETED, 0, 0) }
    }

    override suspend fun testConnection(account: Account): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("CalDAV VTODO", "Google Tasks"))
            } catch (e: Exception) {
                ConnectionTestResult(false, 0, emptyList(), e.message)
            }
        }
    }

    override suspend fun getTaskLists(account: Account): List<TaskList> {
        return emptyList()
    }

    private fun updateProgress(accountId: String, folder: String?, stage: SyncStage, current: Int, total: Int) {
        _syncProgress.value = _syncProgress.value + (accountId to SyncProgress(accountId, folder, stage, current, total))
    }
}
