package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.repository.TaskRepository
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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
                val url = account.serverConfig.caldavUrl ?: return@withContext SyncResult.success(0, emptyList(), emptyList())
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
                val dav = newCalDav(url, auth, client)

                val lists = dav.discoverTaskLists()
                if (lists.isEmpty()) {
                    Log.w("TaskSyncEngineImpl", "No task lists discovered for ${account.email}")
                    return@withContext SyncResult.success(0, emptyList(), emptyList())
                }

                val localTasks = taskRepo.getLocalOnly(account.id)

                var synced = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()

                for (list in lists) {
                    updateProgress(account.id, list.displayName, SyncStage.LISTING_FOLDERS, synced, synced)
                    val etags = dav.getETagList(list.path)
                    val toFetch = etags.filter { entry ->
                        val existing = taskRepo.getByUid(entry.href.substringAfterLast('/').substringBefore('.'), account.id)
                        existing == null || existing.etag != entry.etag
                    }
                    for (entry in toFetch) {
                        val res = dav.fetchItem(account.id, entry.href) ?: continue
                        val uid = entry.href.substringAfterLast('/').substringBefore('.')
                        val parsed = ICalParser.parse(res.ical, account.id, list.path, entry.href)
                        parsed.tasks.firstOrNull() ?: continue
                        val task = parsed.tasks.first().copy(
                            listId = list.path,
                            uid = uid,
                            etag = entry.etag,
                            isLocalOnly = false
                        )
                        val existing = taskRepo.getByUid(uid, account.id)
                        if (existing == null) {
                            taskRepo.insert(task)
                            newItems.add(task.id)
                        } else {
                            taskRepo.update(task.copy(id = existing.id))
                            updatedItems.add(existing.id)
                        }
                        synced++
                    }
                }

                // ponytail: push locally-created tasks (isLocalOnly) that the server has never seen.
                for (local in localTasks) {
                    if (local.uid.isBlank()) continue
                    val listPath = if (local.listId.isNotBlank() && local.listId.contains('/')) local.listId
                    else lists.firstOrNull()?.path ?: continue
                    val href = "$listPath/${local.uid}.ics"
                    val etag = dav.putResource(href, VTaskSerializer.toVtodo(local, local.uid))
                    if (etag != null) {
                        taskRepo.update(local.copy(isLocalOnly = false, needsSync = false, etag = etag))
                        updatedItems.add(local.id)
                        synced++
                    }
                }

                // ponytail: delete tasks that existed locally but vanished server-side during down-sync.
                // (isLocalOnly tasks are kept — they were just pushed above.)
                val serverUids = etagsUids(dav, lists)
                for (list in lists) {
                    val tasks = taskRepo.getByList(account.id, list.path).first()
                    for (local in tasks) {
                        if (!local.isLocalOnly && local.uid.isNotBlank() && local.uid !in serverUids) {
                            taskRepo.delete(local)
                        }
                    }
                }

                updateProgress(account.id, null, SyncStage.COMPLETED, synced, synced)
                SyncResult.success(synced, newItems, updatedItems)
            } catch (e: Exception) {
                Log.e("TaskSyncEngineImpl", "sync failed for ${account.email}: ${e.message}", e)
                updateProgress(account.id, null, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Task sync failed")
            }
        }
    }

    private suspend fun etagsUids(dav: CalDAVClient, lists: List<CalDAVClient.CalendarInfo>): Set<String> {
        val out = mutableSetOf<String>()
        for (list in lists) {
            runCatching { dav.getETagList(list.path) }.getOrDefault(emptyList()).forEach { entry ->
                out += entry.href.substringAfterLast('/').substringBefore('.')
            }
        }
        return out
    }

    override suspend fun syncTaskList(account: Account, taskList: TaskList): SyncResult = syncAccount(account)

    override suspend fun fetchTask(account: Account, listId: String, uid: String): Task? {
        return withContext(Dispatchers.IO) {
            val url = account.serverConfig.caldavUrl ?: return@withContext null
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = newCalDav(url, auth, client)
            val res = dav.fetchItem(account.id, "$listId/$uid.ics") ?: return@withContext null
            ICalParser.parse(res.ical, account.id, listId, res.href).tasks.firstOrNull()
        }
    }

    override suspend fun createTask(account: Account, task: Task): com.unifiedcomms.sync.CreateResult {
        return withContext(Dispatchers.IO) {
            val url = account.serverConfig.caldavUrl ?: return@withContext com.unifiedcomms.sync.CreateResult.failure("No CalDAV URL")
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = newCalDav(url, auth, client)
            val lists = dav.discoverTaskLists()
            val listPath = if (task.listId.isNotBlank() && task.listId.contains('/')) task.listId else lists.firstOrNull()?.path
            listPath ?: return@withContext com.unifiedcomms.sync.CreateResult.failure("No task list")
            val uid = task.uid.ifBlank { java.util.UUID.randomUUID().toString() }
            val href = "$listPath/$uid.ics"
            val etag = dav.putResource(href, VTaskSerializer.toVtodo(task, uid))
                ?: return@withContext com.unifiedcomms.sync.CreateResult.failure("Task write failed")
            com.unifiedcomms.sync.CreateResult.success(uid, uid, etag)
        }
    }

    override suspend fun updateTask(account: Account, task: Task): SyncResult {
        return withContext(Dispatchers.IO) {
            val url = account.serverConfig.caldavUrl ?: return@withContext SyncResult.failure("No CalDAV URL")
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = newCalDav(url, auth, client)
            val lists = dav.discoverTaskLists()
            val listPath = if (task.listId.isNotBlank() && task.listId.contains('/')) task.listId else lists.firstOrNull()?.path
            listPath ?: return@withContext SyncResult.failure("No task list")
            val uid = task.uid.ifBlank { return@withContext SyncResult.failure("Task has no UID") }
            val href = "$listPath/$uid.ics"
            val etag = dav.putResource(href, VTaskSerializer.toVtodo(task, uid))
                ?: return@withContext SyncResult.failure("Task write failed")
            taskRepo.update(task.copy(etag = etag, needsSync = false))
            SyncResult.success()
        }
    }

    override suspend fun deleteTask(account: Account, listId: String, uid: String): SyncResult {
        return withContext(Dispatchers.IO) {
            val url = account.serverConfig.caldavUrl ?: return@withContext SyncResult.failure("No CalDAV URL")
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = newCalDav(url, auth, client)
            val href = "$listId/$uid.ics"
            if (dav.deleteResource(href)) SyncResult.success() else SyncResult.failure("Task delete failed")
        }
    }

    override suspend fun completeTask(account: Account, task: Task): SyncResult =
        updateTask(account, task.copy(status = TaskStatus.COMPLETED, completedAt = com.unifiedcomms.data.model.TaskDateTime.fromInstant(Clock.System.now()), needsSync = true))

    fun allProgress() = _syncProgress.map { it.values.toList() }.distinctUntilChanged()

    override fun observeSyncProgress(accountId: String): kotlinx.coroutines.flow.Flow<SyncProgress> {
        return allProgress().map { list -> list.firstOrNull { it.accountId == accountId } ?: SyncProgress(accountId, null, SyncStage.COMPLETED, 0, 0) }
    }

    override suspend fun testConnection(account: Account): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val url = account.serverConfig.caldavUrl ?: return@withContext ConnectionTestResult(false, 0, emptyList(), "Missing CalDAV URL")
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
                @Suppress("UNUSED_VARIABLE") val lists = newCalDav(url, auth, client).discoverTaskLists()
                ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("CalDAV VTODO"))
            } catch (e: Exception) {
                ConnectionTestResult(false, 0, emptyList(), e.message)
            }
        }
    }

    override suspend fun getTaskLists(account: Account): List<TaskList> {
        return withContext(Dispatchers.IO) {
            val url = account.serverConfig.caldavUrl ?: return@withContext emptyList()
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            newCalDav(url, auth, client).discoverTaskLists().map { info ->
                TaskList(id = info.path, accountId = account.id, serverId = info.path, title = info.displayName)
            }
        }
    }

    private fun updateProgress(accountId: String, folder: String?, stage: SyncStage, current: Int, total: Int) {
        _syncProgress.value = _syncProgress.value + (accountId to SyncProgress(accountId, folder, stage, current, total))
    }

    // ponytail: build a CalDAVClient, preferring an OAuth bearer token when the account is OAUTH2.
    private fun newCalDav(url: String, auth: com.unifiedcomms.data.model.AuthConfig, client: OkHttpClient): CalDAVClient {
        val bearer = if (auth.type == com.unifiedcomms.data.model.AuthType.OAUTH2) auth.oauthAccessToken else null
        return CalDAVClient(url, auth.username ?: "", auth.passwordEncrypted ?: "", client, bearer)
    }
}
