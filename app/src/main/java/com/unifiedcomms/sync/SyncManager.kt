package com.unifiedcomms.sync

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SyncManager(
    private val emailSync: EmailSyncEngine,
    private val calendarSync: CalendarSyncEngine,
    private val taskSync: TaskSyncEngine,
    private val contactSync: ContactSyncEngine,
    private val accountRepo: AccountRepository,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    private val _syncStates = MutableStateFlow<Map<String, SyncState>>(emptyMap())
    val syncStates: StateFlow<Map<String, SyncState>> = _syncStates
    private val periodicJobs = mutableMapOf<String, Job>()

    override fun onStart(owner: LifecycleOwner) {
        scope.launch(Dispatchers.IO) {
            accountRepo.getAllActive().collect { accounts ->
                val currentIds = accounts.map { it.id }.toSet()
                periodicJobs.keys.filter { it !in currentIds }.forEach { cancelPeriodicSync(it) }
                accounts.forEach { account ->
                    if (!periodicJobs.containsKey(account.id)) {
                        schedulePeriodicSync(account)
                    }
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        periodicJobs.values.forEach { it.cancel() }
        periodicJobs.clear()
    }

    private fun schedulePeriodicSync(account: Account) {
        val intervalMs = (account.syncConfig.syncIntervalMinutes * 60 * 1000L)
        val job = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(intervalMs)
                if (_syncStates.value[account.id]?.isSyncing == true) continue
                performFullSync(account)
            }
        }
        periodicJobs[account.id] = job
    }

    private fun cancelPeriodicSync(accountId: String) {
        periodicJobs.remove(accountId)?.cancel()
    }

    data class SyncState(
        val accountId: String,
        val emailProgress: SyncProgress? = null,
        val calendarProgress: SyncProgress? = null,
        val taskProgress: SyncProgress? = null,
        val contactProgress: SyncProgress? = null,
        val isSyncing: Boolean = false,
        val lastSync: Long? = null,
        val lastError: String? = null
    )

    suspend fun performFullSync(account: Account): SyncResult {
        updateState(account.id) { it.copy(isSyncing = true, lastError = null) }

        val results = mutableListOf<SyncResult>()
        val startTime = System.currentTimeMillis()

        try {
            if (account.syncConfig.syncEmail) {
                val result = emailSync.syncAccount(account)
                results.add(result)
                if (!result.success) throw SyncException("Email sync failed: ${result.errorMessage}")
            }

            if (account.syncConfig.syncCalendar) {
                val result = calendarSync.syncAccount(account)
                results.add(result)
                if (!result.success) throw SyncException("Calendar sync failed: ${result.errorMessage}")
            }

            if (account.syncConfig.syncTasks) {
                val result = taskSync.syncAccount(account)
                results.add(result)
                if (!result.success) throw SyncException("Task sync failed: ${result.errorMessage}")
            }

            if (account.syncConfig.syncContacts) {
                val result = contactSync.syncAccount(account)
                results.add(result)
                if (!result.success) throw SyncException("Contact sync failed: ${result.errorMessage}")
            }

            val totalSynced = results.sumOf { it.itemsSynced }
            updateState(account.id) {
                it.copy(
                    isSyncing = false,
                    lastSync = startTime,
                    lastError = null
                )
            }
            return SyncResult.success(totalSynced)

        } catch (e: Exception) {
            updateState(account.id) {
                it.copy(isSyncing = false, lastError = e.message)
            }
            return SyncResult.failure(e.message ?: "Unknown sync error")
        }
    }

    suspend fun syncEmail(account: Account, folder: String? = null): SyncResult {
        updateState(account.id) { it.copy(emailProgress = SyncProgress(account.id, folder, SyncStage.CONNECTING, 0, 0), isSyncing = true) }
        return if (folder != null) emailSync.syncFolder(account, folder) else emailSync.syncAccount(account)
    }

    suspend fun syncCalendar(account: Account): SyncResult {
        updateState(account.id) { it.copy(calendarProgress = SyncProgress(account.id, null, SyncStage.CONNECTING, 0, 0), isSyncing = true) }
        return calendarSync.syncAccount(account)
    }

    suspend fun syncTasks(account: Account): SyncResult {
        updateState(account.id) { it.copy(taskProgress = SyncProgress(account.id, null, SyncStage.CONNECTING, 0, 0), isSyncing = true) }
        return taskSync.syncAccount(account)
    }

    suspend fun syncContacts(account: Account): SyncResult {
        updateState(account.id) { it.copy(contactProgress = SyncProgress(account.id, null, SyncStage.CONNECTING, 0, 0), isSyncing = true) }
        return contactSync.syncAccount(account)
    }

    private fun updateState(accountId: String, transform: (SyncState) -> SyncState) {
        var done = false
        while (!done) {
            val current = _syncStates.value
            val updated = current + (accountId to transform(current[accountId] ?: SyncState(accountId)))
            done = _syncStates.compareAndSet(current, updated)
        }
    }

    fun observeAccountSync(accountId: String): kotlinx.coroutines.flow.Flow<SyncState?> {
        return _syncStates.transform { states: Map<String, SyncState> ->
            emit(states[accountId])
        }.distinctUntilChanged()
    }

    suspend fun testAllConnections(account: Account): Map<String, ConnectionTestResult> = mapOf(
        "email" to emailSync.testConnection(account),
        "calendar" to calendarSync.testConnection(account),
        "tasks" to taskSync.testConnection(account),
        "contacts" to contactSync.testConnection(account)
    )

    class SyncException(message: String) : Exception(message)
}
