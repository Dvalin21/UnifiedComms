package com.unifiedcomms.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.unifiedcomms.util.PreferencesManager
import java.util.concurrent.TimeUnit

/**
 * Owns background-sync cadence. WorkManager's periodic API has a 15-minute
 * floor, so 5–14 minute selections use a constrained one-shot chain instead.
 */
object BackgroundSyncScheduler {

    private const val PERIODIC_WORK_NAME = "unifiedcomms.background.sync"
    private const val SHORT_WORK_NAME = "unifiedcomms.background.sync.short"
    private const val MIN_INTERVAL_MIN = 5L
    private const val MAX_INTERVAL_MIN = 720L
    private const val PERIODIC_MIN = 15L

    fun schedule(
        context: Context,
        intervalMinutes: Long = 15L,
        autoSync: Boolean = true,
        wifiOnly: Boolean = false,
        replaceShort: Boolean = false
    ) {
        val manager = WorkManager.getInstance(context)
        if (!autoSync || intervalMinutes < 0) {
            cancel(context)
            return
        }
        val interval = intervalMinutes.coerceIn(MIN_INTERVAL_MIN, MAX_INTERVAL_MIN)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        if (interval >= PERIODIC_MIN) {
            manager.cancelUniqueWork(SHORT_WORK_NAME)
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            manager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            manager.cancelUniqueWork(PERIODIC_WORK_NAME)
            val request = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setInitialDelay(interval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            if (replaceShort) manager.cancelUniqueWork(SHORT_WORK_NAME)
            manager.enqueueUniqueWork(
                SHORT_WORK_NAME,
                if (replaceShort) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    /** Queue the next short interval after a successful short worker run. */
    fun scheduleNextShort(context: Context) {
        val prefs = PreferencesManager.getInstance()
        val interval = prefs.getSyncIntervalMinutes(15).toLong()
        if (!prefs.getBoolean("auto_sync", true) || interval !in MIN_INTERVAL_MIN until PERIODIC_MIN) return
        val request = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (prefs.getBoolean("sync_wifi_only", false)) NetworkType.UNMETERED
                        else NetworkType.CONNECTED
                    )
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SHORT_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(PERIODIC_WORK_NAME)
        manager.cancelUniqueWork(SHORT_WORK_NAME)
    }
}
