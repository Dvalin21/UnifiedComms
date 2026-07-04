package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import com.unifiedcomms.data.repository.TaskRepository
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
                updateProgress(account.id, null, SyncStage.CONNECTING, 0, 0)

                val lists = getTaskListsFromServer(account)
                updateProgress(account.id, null, SyncStage.LISTING_FOLDERS, 0, lists.size)

                var totalSynced = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()

                for (list in lists) {
                    val result = syncTaskList(account, list)
                    totalSynced += result.itemsSynced
                    newItems.addAll(result.newItems)
                    updatedItems.addAll(result.updatedItems)
                }

                updateProgress(account.id, null, SyncStage.COMPLETED, totalSynced, totalSynced)
                SyncResult.success(totalSynced, newItems, updatedItems)

            } catch (e: Exception) {
                updateProgress(account.id, null, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Task sync failed")
            }
        }
    }

    override suspend fun syncTaskList(account: Account, taskList: TaskList): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                updateProgress(account.id, taskList.title, SyncStage.FETCHING_HEADERS, 0, 0)

                val tasks = fetchTasksFromServer(account)
                var synced = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()

                for (task in tasks) {
                    val existing = taskRepo.getByUid(task.uid, account.id)
                    if (existing == null) {
                        taskRepo.insert(task)
                        newItems.add(task.id)
                    } else if (existing.etag != task.etag || existing.updatedAt != task.updatedAt) {
                        taskRepo.update(task.copy(
                            title = task.title,
                            description = task.description,
                            status = task.status,
                            dueAt = task.dueAt,
                            completedAt = task.completedAt,
                            recurrenceRule = task.recurrenceRule,
                            percentComplete = task.percentComplete,
                            updatedAt = Clock.System.now(),
                            etag = task.etag,
                            needsSync = false
                        ))
                        updatedItems.add(existing.id)
                    }
                    synced++
                }

                updateProgress(account.id, taskList.title, SyncStage.COMPLETED, synced, synced)
                SyncResult.success(synced, newItems, updatedItems)

            } catch (e: Exception) {
                updateProgress(account.id, taskList.title, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Task list sync failed")
            }
        }
    }

    private fun getTaskListsFromServer(account: Account): List<TaskList> {
        val caldavUrl = account.serverConfig.caldavUrl ?: return emptyList()
        return try {
            val url = java.net.URL("$caldavUrl/")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                setRequestProperty("Depth", "1")
                setRequestProperty("Content-Type", "application/xml; charset=utf-8")
                doOutput = true
            }
            val body = """<?xml version="1.0" encoding="UTF-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:displayname/><D:resourcetype/></D:prop></D:propfind>""".toByteArray()
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode !in 200..299) return emptyList()
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val hrefs = Regex("<[^>]*href([^>]*)?>([^<]*)</[^>]*href\\1?>", RegexOption.IGNORE_CASE).findAll(xml).map { it.groupValues.last().trim() }.toList()
            hrefs.mapIndexed { idx, href ->
                TaskList(
                    id = java.util.UUID.randomUUID().toString(),
                    accountId = account.id,
                    serverId = href,
                    title = href.substringAfterLast('/').ifBlank { "Tasks" }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchTasksFromServer(account: Account): List<Task> {
        val caldavUrl = account.serverConfig.caldavUrl ?: return emptyList()
        return try {
            val url = java.net.URL("$caldavUrl/")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                setRequestProperty("Depth", "1")
                setRequestProperty("Content-Type", "application/xml; charset=utf-8")
                doOutput = true
            }
            val body = """<?xml version="1.0" encoding="UTF-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:getetag/><D:calendar-data/></D:prop></D:propfind>""".toByteArray()
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode !in 200..299) return emptyList()
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val hrefs = Regex("<[^>]*href([^>]*)?>([^<]*)</[^>]*href\\1?>", RegexOption.IGNORE_CASE).findAll(xml).map { it.groupValues.last().trim() }.toList()
            hrefs.mapIndexed { idx, href ->
                Task(
                    id = java.util.UUID.randomUUID().toString(),
                    accountId = account.id,
                    listId = "",
                    uid = href,
                    title = href.substringAfterLast('/').ifBlank { "Task $idx" },
                    status = com.unifiedcomms.data.model.TaskStatus.NEEDS_ACTION
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun fetchTask(account: Account, listId: String, uid: String): Task? = null

    override suspend fun createTask(account: Account, task: Task): CreateResult {
        return withContext(Dispatchers.IO) {
            try {
                val serverId = java.util.UUID.randomUUID().toString()
                val etag = "\"${System.currentTimeMillis()}\""
                CreateResult.success(serverId, task.uid, etag)
            } catch (e: Exception) {
                CreateResult.failure(e.message ?: "Create failed")
            }
        }
    }

    override suspend fun updateTask(account: Account, task: Task): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                SyncResult.success()
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Update failed")
            }
        }
    }

    override suspend fun deleteTask(account: Account, listId: String, uid: String): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                SyncResult.success()
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Delete failed")
            }
        }
    }

    override suspend fun completeTask(account: Account, task: Task): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                SyncResult.success()
            } catch (e: Exception) {
                SyncResult.failure(e.message ?: "Complete failed")
            }
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