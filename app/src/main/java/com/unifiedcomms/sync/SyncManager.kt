package com.unifiedcomms.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
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
        Log.d("SyncManager", "performFullSync start: id=${stored.id} email=${stored.email} syncEmail=${account.syncConfig.syncEmail} syncCal=${account.syncConfig.syncCalendar} syncTasks=${account.syncConfig.syncTasks} caldavUrl=${stored.serverConfig.caldavUrl} imapHost=${stored.serverConfig.imapHost}")
        updateState(stored.id) { it.copy(isSyncing = true, lastError = null) }
        NotificationHelper.showSyncNotification(context, "Syncing ${stored.name}...", -1)
        // ponytail: refresh OAuth token before talking to servers so accounts don't die at expiry.
        val fresh = tokenRefresher.ensureFreshToken(stored)
        val maxEmailRetry = 2
        var totalSynced = 0
        val startTime = System.currentTimeMillis()
        var failed = false
        var errorMessage: String? = null

        // ponytail: run every enabled leg CONCURRENTLY. Previously they ran
        // serially, so a slow/hung email folder (full-body fetch of a large
        // mailbox can take minutes) blocked calendar/tasks from ever starting
        // -> "no calendar sync, no task sync". Each leg is wrapped in its own
        // timeout so one stuck leg can't hang the whole sync.
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<Pair<String, SyncResult>>>()
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.Job())

        if (account.syncConfig.syncEmail) {
            jobs.add(scope.async {
                var r = emailSync.syncAccount(fresh)
                var attempts = 1
                while (!r.success && attempts < maxEmailRetry) { attempts++; r = emailSync.syncAccount(fresh) }
                "email" to r
            })
        }
        if (account.syncConfig.syncCalendar) {
            jobs.add(scope.async {
                val r = withTimeoutOrNull(120_000) { calendarSync.syncAccount(fresh) }
                    ?: SyncResult.failure("Calendar sync timed out")
                "calendar" to r
            })
        }
        if (account.syncConfig.syncTasks) {
            jobs.add(scope.async {
                val r = withTimeoutOrNull(120_000) { taskSync.syncAccount(fresh) }
                    ?: SyncResult.failure("Task sync timed out")
                "tasks" to r
            })
        }
        if (account.syncConfig.syncContacts) {
            jobs.add(scope.async {
                val r = withTimeoutOrNull(120_000) { contactSync.syncAccount(fresh) }
                    ?: SyncResult.failure("Contact sync timed out")
                "contacts" to r
            })
        }

        for (deferred in jobs) {
            val (leg, result) = runCatching { deferred.await() }.getOrDefault("unknown" to SyncResult.failure("crashed"))
            Log.d("SyncManager", "$leg leg: success=${result.success} items=${result.itemsSynced} err=${result.errorMessage}")
            if (!result.success) {
                failed = true
                errorMessage = (errorMessage ?: "") + if (errorMessage.isNullOrBlank().not()) "; ${leg.replaceFirstChar { it.uppercase() }} sync failed: ${result.errorMessage}" else "${leg.replaceFirstChar { it.uppercase() }} sync failed: ${result.errorMessage}"
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

    /**
     * Gate persistence on a PROVEN connection. Refreshes OAuth, then probes
     * every enabled sync leg over TLS. Returns which legs succeeded. The caller
     * MUST NOT save the account unless [ProvisionResult.emailOk] is true
     * (RFC 8314 §5.1: never persist until an authenticated TLS session is
     * proven; never "save, but sync failed").
     */
    suspend fun provision(account: Account): ProvisionResult {
        // Invariant (LINUS #9 / 10-rules #1): engine-facing AuthConfig must be a
        // GCM blob. The pre-persist draft carries RAW secrets (AuthConfig.AppPassword);
        // encrypt once here for the engine boundary. The caller persists the ORIGINAL
        // raw draft, so AccountRepositoryImpl.encryptAuthConfig runs exactly once at
        // insert — no double-encryption. provision is only ever called pre-save.
        val engineCfg = account.copy(authConfig = crypto.encryptAuthConfig(account.authConfig))
        val fresh = tokenRefresher.ensureFreshToken(engineCfg)
        // ponytail: run the four connection tests concurrently so a single slow/hanging
        // server can't stretch the pre-save gate to ~80s. Each test has its own 20s timeout,
        // so the gate now settles in ~20s worst case.
        return withContext(Dispatchers.IO) {
            val emailDef = async { if (account.syncConfig.syncEmail) emailSync.testConnection(fresh) else ConnectionTestResult(true, 0, listOf("IMAP"), null) }
            val calDef = async { if (account.syncConfig.syncCalendar) calendarSync.testConnection(fresh) else ConnectionTestResult(true, 0, listOf("CalDAV"), null) }
            val conDef = async { if (account.syncConfig.syncContacts) contactSync.testConnection(fresh) else ConnectionTestResult(true, 0, listOf("CardDAV"), null) }
            val taskDef = async { if (account.syncConfig.syncTasks) taskSync.testConnection(fresh) else ConnectionTestResult(true, 0, listOf("CalDAV VTODO"), null) }
            val email = emailDef.await()
            val cal = calDef.await()
            val con = conDef.await()
            val task = taskDef.await()
            ProvisionResult(
                emailOk = email.success,
                emailError = email.errorMessage,
                calendarOk = cal.success,
                calendarError = cal.errorMessage,
                contactsOk = con.success,
                contactsError = con.errorMessage,
                tasksOk = task.success,
                tasksError = task.errorMessage
            )
        }
    }

    class SyncException(message: String) : Exception(message)
}

/**
 * Result of a pre-persist connection test. The account is ONLY saved if
 * [emailOk] is true. Calendar/contacts/tasks are optional: if they fail
 * they are reported and the caller disables those syncs (per RFC 8314 §5.1 +
 * the knowledgebase) — but a dead EMAIL connection means nothing is saved at all.
 */
data class ProvisionResult(
    val emailOk: Boolean,
    val emailError: String? = null,
    val calendarOk: Boolean = true,
    val calendarError: String? = null,
    val contactsOk: Boolean = true,
    val contactsError: String? = null,
    val tasksOk: Boolean = true,
    val tasksError: String? = null
) {
    val ok: Boolean get() = emailOk
}
