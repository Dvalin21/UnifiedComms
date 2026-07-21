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
import com.unifiedcomms.data.repository.MessagingRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

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
        val messagingRepo = MessagingRepositoryImpl(db.messageDao(), db.conversationDao())
        val contactRepo = ContactRepositoryImpl(db.contactDao())

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val syncManager = SyncManager(
                EmailSyncEngineImpl(emailRepo, accountRepo, crypto, scope),
                CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, scope),
                TaskSyncEngineImpl(taskRepo, accountRepo, crypto, scope),
                ContactSyncEngineImpl(contactRepo, accountRepo, crypto, scope),
                ChatSyncEngineImpl(messagingRepo, accountRepo, crypto, scope, applicationContext),
                accountRepo,
                scope,
                applicationContext,
                crypto
            )

            val accounts = accountRepo.getAllActive().first()
            if (accounts.isEmpty()) return Result.success()

            var failedAccounts = 0
            for (account in accounts) {
                val result = runCatching { syncManager.performFullSync(account) }
                if (result.isFailure) {
                    Log.e("BackgroundSyncWorker", "Account ${account.email} sync failed", result.exceptionOrNull())
                    failedAccounts++
                }
            }

            return if (failedAccounts == 0) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e("BackgroundSyncWorker", "Background sync error", e)
            return Result.retry()
        } finally {
            scope.cancel()
        }
    }
}
