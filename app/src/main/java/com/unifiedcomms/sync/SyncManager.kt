package com.unifiedcomms.sync

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.util.NotificationHelper
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
    private val scope: CoroutineScope,
    private val context: Context,
    private val crypto: com.unifiedcomms.security.CryptoManager
) : DefaultLifecycleObserver {

    private val tokenRefresher = OAuthTokenRefresher(accountRepo, crypto)

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
        val intervalMs = (account.syncConfig.syncIntervalMinutes.coerceAtLeast(5) * 60_000L)
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
        // ponytail: the in-memory account carries PLAINTEXT credentials (UI built it).
        // The persisted record holds the encrypted form; re-fetch so the engines
        // receive ciphertext they can decrypt. Skipping this made every UI-added
        // account fail to auth.
        val stored = accountRepo.getById(account.id) ?: account
        updateState(stored.id) { it.copy(isSyncing = true, lastError = null) }
        NotificationHelper.showSyncNotification(context, "Syncing ${stored.name}...", -1)
        // ponytail: refresh OAuth token before talking to servers so accounts don't die at expiry.
        val fresh = tokenRefresher.ensureFreshToken(stored)
        val maxEmailRetries = 2
        var totalSynced = 0
        val startTime = System.currentTimeMillis()
        var failed = false
        var errorMessage: String? = null

        if (account.syncConfig.syncEmail) {
            var emailResult = emailSync.syncAccount(fresh)
            var attempts = 1
            while (!emailResult.success && attempts < maxEmailRetries) {
                attempts++
                emailResult = emailSync.syncAccount(fresh)
            }
            if (!emailResult.success) {
                failed = true
                errorMessage = emailResult.errorMessage ?: "Email sync failed"
            } else totalSynced += emailResult.itemsSynced
        }

        if (account.syncConfig.syncCalendar) {
            val result = calendarSync.syncAccount(fresh)
            if (!result.success) {
                failed = true
                errorMessage = (errorMessage ?: "") + if (errorMessage.isNullOrBlank().not()) "; Calendar sync failed: ${result.errorMessage}" else "Calendar sync failed: ${result.errorMessage}"
            } else totalSynced += result.itemsSynced
        }

        if (account.syncConfig.syncTasks) {
            val result = taskSync.syncAccount(fresh)
            if (!result.success) {
                failed = true
                errorMessage = (errorMessage ?: "") + if (errorMessage.isNullOrBlank().not()) "; Task sync failed: ${result.errorMessage}" else "Task sync failed: ${result.errorMessage}"
            } else totalSynced += result.itemsSynced
        }

        if (account.syncConfig.syncContacts) {
            val result = contactSync.syncAccount(fresh)
            if (!result.success) {
                failed = true
                errorMessage = (errorMessage ?: "") + if (errorMessage.isNullOrBlank().not()) "; Contact sync failed: ${result.errorMessage}" else "Contact sync failed: ${result.errorMessage}"
            } else totalSynced += result.itemsSynced
        }

        updateState(account.id) {
            it.copy(
                isSyncing = false,
                lastSync = startTime,
                lastError = if (failed) errorMessage else null
            )
        }
        if (failed) {
            NotificationHelper.showSyncNotification(context, "Sync failed: ${errorMessage ?: "Unknown error"}", -1)
            return SyncResult.failure(errorMessage ?: "Sync failed")
        }
        NotificationHelper.showSyncNotification(context, "Sync completed", 100)
        return SyncResult.success(totalSynced)
    }

    suspend fun syncNow(account: Account): SyncResult {
        return performFullSync(account)
    }

    suspend fun sendEmail(account: Account, email: com.unifiedcomms.data.model.Email): SendResult =
        emailSync.sendEmail(account, email)

    suspend fun moveEmail(account: Account, uids: List<String>, fromFolder: String, toFolder: String): SyncResult =
        emailSync.moveToFolder(account, uids, fromFolder, toFolder)

    suspend fun deleteEmail(account: Account, folder: String, uids: List<String>): SyncResult =
        emailSync.deleteMessages(account, folder, uids)

    suspend fun syncEmail(account: Account, folder: String? = null): SyncResult {
        updateState(account.id) { it.copy(emailProgress = SyncProgress(account.id, folder, SyncStage.CONNECTING, 0, 0), isSyncing = true) }
        NotificationHelper.showSyncNotification(context, "Syncing email ${account.name}...", -1)
        return if (folder != null) emailSync.syncFolder(account, folder) else emailSync.syncAccount(account)
    }

    suspend fun syncCalendar(account: Account): SyncResult {
        updateState(account.id) { it.copy(calendarProgress = SyncProgress(account.id, null, SyncStage.CONNECTING, 0, 0), isSyncing = true) }
        NotificationHelper.showSyncNotification(context, "Syncing calendar ${account.name}...", -1)
        return calendarSync.syncAccount(account)
    }

    suspend fun syncTasks(account: Account): SyncResult {
        updateState(account.id) { it.copy(taskProgress = SyncProgress(account.id, null, SyncStage.CONNECTING, 0, 0), isSyncing = true) }
        NotificationHelper.showSyncNotification(context, "Syncing tasks ${account.name}...", -1)
        return taskSync.syncAccount(account)
    }

    suspend fun syncContacts(account: Account): SyncResult {
        updateState(account.id) { it.copy(contactProgress = SyncProgress(account.id, null, SyncStage.CONNECTING, 0, 0), isSyncing = true) }
        return contactSync.syncAccount(account)
    }

    private fun updateState(accountId: String, transform: (SyncState) -> SyncState) {
        val current = _syncStates.value
        val updated = current + (accountId to transform(current[accountId] ?: SyncState(accountId)))
        _syncStates.value = updated
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
