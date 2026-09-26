package com.unifiedcomms.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unifiedcomms.UnifiedCommsApplication
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.ContactRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.reminder.ReminderScheduler
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

internal fun shouldRetrySync(result: Result<SyncResult>): Boolean =
    result.isFailure || result.getOrNull()?.success != true

/**
 * Retry ceiling. WorkManager will retry a failing one-shot forever with backoff, so a permanent
 * error (renamed folder, revoked credentials, deleted account) becomes a poison pill that burns
 * battery and fills the log. Past this many attempts the failure is reported and the work is
 * finished; the next scheduled cycle will try again from a clean slate.
 */
internal const val MAX_SYNC_ATTEMPTS = 5

internal fun shouldGiveUpRetrying(runAttemptCount: Int): Boolean = runAttemptCount >= MAX_SYNC_ATTEMPTS

/**
 * Background sync driver. Reuses the SAME engine stack + SyncManager.performFullSync
 * that the foreground UI uses, so behaviour is identical — only the lifecycle owner
 * differs (WorkManager process instead of the app's on-screen lifecycle).
 *
 * ponytail: the foreground SyncManager (built in MainViewModel) does NOT survive process
 * death, and its onStop cancels the periodic coroutine loop. This worker is the real
 * background path: WorkManager keeps it alive across process death and respects Doze.
 */
class BackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = UnifiedCommsDatabase.getInstance(applicationContext)
        val crypto = CryptoManagerImpl(applicationContext)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val taskRepo = TaskRepositoryImpl(db.taskDao(), db.taskListDao())
        val contactRepo = ContactRepositoryImpl(db.contactDao())

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val syncManager = SyncManager(
                EmailSyncEngineImpl(emailRepo, accountRepo, crypto, scope, calendarRepo),
                CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, scope),
                TaskSyncEngineImpl(taskRepo, accountRepo, crypto, scope),
                ContactSyncEngineImpl(contactRepo, accountRepo, crypto, scope),
                accountRepo,
                applicationContext,
                crypto
            )

            val accounts = accountRepo.getAllActive().first()
            if (accounts.isEmpty()) {
                BackgroundSyncScheduler.scheduleNextShort(applicationContext)
                return Result.success()
            }

            var failedAccounts = 0
            for (snapshot in accounts) {
                val account = accountRepo.getById(snapshot.id)?.takeIf { it.isActive } ?: continue
                val result: kotlin.Result<SyncResult> = try {
                    kotlin.Result.success(syncManager.performFullSync(account))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    kotlin.Result.failure(e)
                }
                if (result.isSuccess && result.getOrNull()?.success == true) {
                    ReminderScheduler(applicationContext, calendarRepo, accountRepo).scheduleReminders(account.id)
                }
                if (shouldRetrySync(result)) {
                    result.exceptionOrNull()?.let { Log.e("BackgroundSyncWorker", "Account ${account.email} sync failed", it) }
                        ?: Log.e("BackgroundSyncWorker", "Account ${account.email} sync failed: ${result.getOrNull()?.errorMessage}")
                    failedAccounts++
                }
            }

            return if (failedAccounts == 0) {
                BackgroundSyncScheduler.scheduleNextShort(applicationContext)
                Result.success()
            } else if (shouldGiveUpRetrying(runAttemptCount)) {
                Log.w(
                    "BackgroundSyncWorker",
                    "giving up after $runAttemptCount attempts ($failedAccounts account(s) failed); " +
                        "the next scheduled cycle will try again"
                )
                BackgroundSyncScheduler.scheduleNextShort(applicationContext)
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("BackgroundSyncWorker", "Background sync error", e)
            return if (shouldGiveUpRetrying(runAttemptCount)) Result.failure() else Result.retry()
        } finally {
            scope.cancel()
        }
    }
}
