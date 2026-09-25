package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.repository.TaskRepository
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CancellationException
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
                val url = account.serverConfig.caldavUrl ?: return@withContext SyncResult.failure("Missing CalDAV URL")
                val auth = crypto.decryptAuthConfig(account.authConfig)
                val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
                val dav = newCalDav(url, auth, client)

                val lists = dav.discoverTaskLists()
                if (lists.isEmpty()) {
                    Log.w("TaskSyncEngineImpl", "No task lists discovered for ${account.email}")
                    return@withContext SyncResult.failure("No task lists discovered (check CalDAV VTODO support)")
                }

                // Include all cached rows, not only pending rows, so unchanged
                // resources can be matched by their exact server href.
                val localById = linkedMapOf<String, Task>()
                for (list in lists) {
                    taskRepo.getByList(account.id, list.path.trimEnd('/')).first().forEach { localById[it.id] = it }
                }
                taskRepo.getNeedingSync(account.id).forEach { localById[it.id] = it }
                var localTasks = localById.values.toMutableList()
                val pushedPaths = mutableSetOf<String>()

                // Push local edits before downloading. A later GET must not erase
                // a pending change when the remote resource is unchanged or slow.
                for (local in localTasks.filter { it.isLocalOnly || it.needsSync }) {
                    if (local.uid.isBlank()) continue
                    val listPath = resolveListPath(taskRepo, account, dav, local.listId, lists)
                        ?: return@withContext SyncResult.failure("No task-list path for ${local.uid}")
                    val push = local.copy(listId = listPath)
                    val href = VTaskSerializer.hrefFor(push)
                    val etag = dav.putResource(href, VTaskSerializer.toVtodo(push, push.uid), ifMatch = push.etag)
                        ?: return@withContext SyncResult.failure("Task write failed for ${local.uid}")
                    val stored = push.copy(
                        serverHref = href,
                        etag = etag,
                        isLocalOnly = false,
                        needsSync = false
                    )
                    if (taskRepo.update(stored) == 0) taskRepo.insert(stored)
                    val index = localTasks.indexOfFirst { it.id == stored.id }
                    if (index >= 0) localTasks[index] = stored else localTasks += stored
                    pushedPaths += pathOf(href)
                }

                val localTaskByPath = localTasks.associateBy { pathOf(VTaskSerializer.hrefFor(it)) }
                val serverPaths = mutableSetOf<String>()
                var synced = 0
                var itemFailures = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()

                for (list in lists) {
                    updateProgress(account.id, list.displayName, SyncStage.LISTING_FOLDERS, synced, synced)
                    val etags = dav.getTaskETagList(list.path).getOrElse {
                        dav.getETagList(list.path).getOrElse { fallback ->
                            return@withContext SyncResult.failure(fallback.message ?: "Task collection listing failed")
                        }
                    }
                    serverPaths += etags.map { pathOf(it.href) }
                    val toFetch = etags.filter { entry ->
                        val path = pathOf(entry.href)
                        val local = localTaskByPath[path]
                        local == null || (local.etag != entry.etag && path !in pushedPaths)
                    }
                    for (entry in toFetch) {
                        val res = dav.fetchItem(account.id, entry.href)
                        if (res == null) {
                            itemFailures++
                            continue
                        }
                        val parsed = ICalParser.parse(res.ical, account.id, list.path, entry.href).tasks.firstOrNull()
                        if (parsed == null) {
                            itemFailures++
                            continue
                        }
                        val listId = list.path.trimEnd('/')
                        val pathExisting = localTaskByPath[pathOf(entry.href)]
                        val existing = pathExisting
                            ?: taskRepo.getByUidAndList(parsed.uid, account.id, listId)
                            ?: taskRepo.getByUid(parsed.uid, account.id)?.takeIf { it.listId == listId }
                        val task = parsed.copy(
                            id = existing?.id ?: parsed.id,
                            listId = listId,
                            position = existing?.position ?: parsed.position,
                            serverHref = res.href,
                            etag = entry.etag,
                            isLocalOnly = false,
                            // Preserve fields not represented by the minimal VTODO parser.
                            completedAt = parsed.completedAt,
                            recurrenceRule = existing?.recurrenceRule ?: parsed.recurrenceRule,
                            recurrenceExceptions = existing?.recurrenceExceptions ?: parsed.recurrenceExceptions,
                            assignee = existing?.assignee ?: parsed.assignee,
                            attachments = existing?.attachments ?: parsed.attachments,
                            categories = parsed.categories,
                            relatedEmails = existing?.relatedEmails ?: parsed.relatedEmails,
                            relatedEvents = existing?.relatedEvents ?: parsed.relatedEvents,
                            parentTaskId = existing?.parentTaskId ?: parsed.parentTaskId,
                            hasSubtasks = existing?.hasSubtasks ?: parsed.hasSubtasks,
                            subtaskCount = existing?.subtaskCount ?: parsed.subtaskCount,
                            completedSubtaskCount = existing?.completedSubtaskCount ?: parsed.completedSubtaskCount,
                            location = parsed.location,
                            geoLocation = existing?.geoLocation ?: parsed.geoLocation,
                            reminderMinutesBefore = existing?.reminderMinutesBefore ?: parsed.reminderMinutesBefore,
                            estimatedDurationMinutes = existing?.estimatedDurationMinutes ?: parsed.estimatedDurationMinutes,
                            actualDurationMinutes = existing?.actualDurationMinutes ?: parsed.actualDurationMinutes
                        )
                        if (existing == null) {
                            taskRepo.insert(task)
                            newItems.add(task.id)
                        } else {
                            taskRepo.update(task)
                            updatedItems.add(task.id)
                        }
                        val localIndex = localTasks.indexOfFirst { it.id == task.id }
                        if (localIndex >= 0) localTasks[localIndex] = task else localTasks += task
                        synced++
                    }
                }

                if (itemFailures > 0) {
                    updateProgress(account.id, null, SyncStage.ERROR, synced, synced)
                    return@withContext SyncResult.failure("$itemFailures task items could not be fetched", itemFailures)
                }

                // Only prune rows from collections whose listing succeeded. A PUT
                // performed in this pass is protected from eventual-consistency loss.
                for (list in lists) {
                    val tasks = taskRepo.getByList(account.id, list.path.trimEnd('/')).first()
                    for (local in tasks) {
                        if (local.isLocalOnly) continue
                        val path = pathOf(VTaskSerializer.hrefFor(local))
                        if (path !in serverPaths && path !in pushedPaths) taskRepo.delete(local)
                    }
                }

                updateProgress(account.id, null, SyncStage.COMPLETED, synced, synced)
                SyncResult.success(synced, newItems, updatedItems)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TaskSyncEngineImpl", "sync failed for ${account.email}: ${e.message}", e)
                updateProgress(account.id, null, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Task sync failed")
            }
        }
    }

    override suspend fun syncTaskList(account: Account, taskList: TaskList): SyncResult = syncAccount(account)

    override suspend fun fetchTask(account: Account, listId: String, uid: String): Task? {
        return withContext(Dispatchers.IO) {
            val url = account.serverConfig.caldavUrl ?: return@withContext null
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = newCalDav(url, auth, client)
            val listPath = resolveListPath(taskRepo, account, dav, listId) ?: return@withContext null
            val local = taskRepo.getByUid(uid, account.id)?.takeIf { sameDavPath(it.listId, listPath) }
            val href = local?.serverHref?.takeIf { it.isNotBlank() } ?: "$listPath/$uid.ics"
            val res = dav.fetchItem(account.id, href) ?: return@withContext null
            ICalParser.parse(res.ical, account.id, listPath, res.href)
                .tasks.firstOrNull()
                ?.copy(etag = res.etag, serverHref = res.href)
        }
    }

    override suspend fun createTask(account: Account, task: Task): com.unifiedcomms.sync.CreateResult {
        return withContext(Dispatchers.IO) {
            val url = account.serverConfig.caldavUrl ?: return@withContext com.unifiedcomms.sync.CreateResult.failure("No CalDAV URL")
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = newCalDav(url, auth, client)
            val listPath = resolveListPath(taskRepo, account, dav, task.listId)
                ?: return@withContext com.unifiedcomms.sync.CreateResult.failure("No task list")
            val uid = task.uid.ifBlank { java.util.UUID.randomUUID().toString() }
            val href = VTaskSerializer.hrefFor(task.copy(listId = listPath, uid = uid))
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
            if (task.uid.isBlank()) return@withContext SyncResult.failure("Task has no UID")
            val listPath = resolveListPath(taskRepo, account, dav, task.listId)
                ?: return@withContext SyncResult.failure("No task list")
            val stored = taskRepo.getByUidAndList(task.uid, account.id, listPath)
                ?: taskRepo.getByUid(task.uid, account.id)?.takeIf { it.listId.trimEnd('/') == listPath.trimEnd('/') }
            val candidate = if (stored == null) task else task.copy(
                id = stored.id,
                uid = stored.uid,
                listId = listPath,
                serverHref = task.serverHref ?: stored.serverHref,
                etag = task.etag ?: stored.etag
            ).withDueAt(task.dueAt)
            val href = VTaskSerializer.hrefFor(candidate)
            val etag = dav.putResource(href, VTaskSerializer.toVtodo(candidate, candidate.uid), ifMatch = candidate.etag)
                ?: return@withContext SyncResult.failure("Task write failed")
            taskRepo.update(candidate.copy(
                serverHref = href,
                etag = etag,
                isLocalOnly = false,
                needsSync = false
            ))
            SyncResult.success()
        }
    }

    override suspend fun deleteTask(account: Account, listId: String, uid: String): SyncResult {
        return withContext(Dispatchers.IO) {
            val requestedPath = listId.takeIf { isDavCollectionPath(it) }
            val local = taskRepo.getByUidAndList(uid, account.id, requestedPath ?: listId)
                ?: requestedPath?.let { taskRepo.getByUid(uid, account.id)?.takeIf { sameDavPath(it.listId, requestedPath) } }
            if (local?.isLocalOnly == true) {
                taskRepo.delete(local)
                return@withContext SyncResult.success()
            }
            val url = account.serverConfig.caldavUrl ?: return@withContext SyncResult.failure("No CalDAV URL")
            val auth = crypto.decryptAuthConfig(account.authConfig)
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val dav = newCalDav(url, auth, client)
            val listPath = local?.listId?.takeIf { isDavCollectionPath(it) }
                ?: resolveListPath(taskRepo, account, dav, listId)
                ?: return@withContext SyncResult.failure("No task list path")
            val resolvedLocal = local
                ?: taskRepo.getByUidAndList(uid, account.id, listPath)
                ?: taskRepo.getByUid(uid, account.id)?.takeIf { it.listId.trimEnd('/') == listPath.trimEnd('/') }
            val href = resolvedLocal?.serverHref?.trim()?.takeIf { it.isNotBlank() }
                ?: "$listPath/$uid.ics"
            if (resolvedLocal == null || !resolvedLocal.isLocalOnly) {
                if (!dav.deleteResource(href)) return@withContext SyncResult.failure("Task delete failed")
            }
            resolvedLocal?.let { taskRepo.delete(it) }
            SyncResult.success()
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
                val lists = newCalDav(url, auth, client).discoverTaskLists()
                if (lists.isEmpty()) {
                    ConnectionTestResult(false, System.currentTimeMillis() - start, emptyList(), "No task lists discovered (check CalDAV VTODO support)")
                } else {
                    ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("CalDAV VTODO"))
                }
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
            }.also { lists ->
                for (list in lists) {
                    if (taskRepo.getListByServerId(account.id, list.serverId) == null) {
                        taskRepo.insertList(list)
                    }
                }
            }
        }
    }

    private suspend fun resolveListPath(
        repository: TaskRepository,
        account: Account,
        dav: CalDAVClient,
        requested: String,
        knownLists: List<CalDAVClient.CalendarInfo>? = null
    ): String? {
        val lists = knownLists ?: dav.discoverTaskLists()
        val requestedPath = requested.takeIf { isDavCollectionPath(it) }?.trimEnd('/')
        if (requestedPath != null) {
            lists.firstOrNull { sameDavPath(it.path, requestedPath) }
                ?.let { return it.path.trimEnd('/') }
        }
        val repositoryList = repository.getListById(requested)
            ?: repository.getListByServerId(account.id, requested)
        if (repositoryList != null) {
            lists.firstOrNull { sameDavPath(it.path, repositoryList.serverId) }
                ?.let { return it.path.trimEnd('/') }
        }
        val name = requested.trim()
        lists.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
            ?.let { return it.path.trimEnd('/') }
        if (name.isBlank() || name.equals("local", ignoreCase = true)) {
            lists.firstOrNull()?.path?.trimEnd('/')?.let { return it }
        }
        return null
    }

    private fun pathOf(href: String): String {
        if (href.isBlank()) return ""
        return runCatching { java.net.URI(href).path }
            .getOrDefault(href.substringAfterLast('/').let { if (it.contains('.')) "/$it" else it })
    }

    private fun isDavCollectionPath(value: String): Boolean =
        value.startsWith("http://", true) ||
            value.startsWith("https://", true) ||
            value.startsWith("/") ||
            (value.contains('/') && !value.equals("local", ignoreCase = true))

    private fun sameDavPath(left: String, right: String): Boolean = runCatching {
        java.net.URI(left).normalize().path.trimEnd('/') ==
            java.net.URI(right).normalize().path.trimEnd('/')
    }.getOrElse {
        left.trimEnd('/').equals(right.trimEnd('/'), ignoreCase = true)
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
