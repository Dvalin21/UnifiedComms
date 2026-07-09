package com.unifiedcomms.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns the single periodic background-sync WorkRequest.
 *
 * ponytail: one unique work chain ("unifiedcomms.background.sync") with KEEP policy, so
 * re-enqueuing on every app start is a no-op once scheduled. WorkManager respects Doze
 * and battery saver; the 15-min floor is the framework minimum for periodic work.
 */
object BackgroundSyncScheduler {

    private const val WORK_NAME = "unifiedcomms.background.sync"
    private const val MIN_INTERVAL_MIN = 15L

    fun schedule(context: Context, intervalMinutes: Long = MIN_INTERVAL_MIN) {
        val interval = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MIN)
        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(interval, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
