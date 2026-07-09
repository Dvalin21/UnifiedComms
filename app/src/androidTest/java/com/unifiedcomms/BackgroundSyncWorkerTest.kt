package com.unifiedcomms

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 15 verification: the background sync worker constructs the real engine stack
 * and SyncManager, then runs performFullSync over active accounts without crashing.
 * On a clean emulator there are no accounts -> the worker short-circuits to success,
 * which still proves the worker + SyncManager wiring executes end-to-end on-device.
 */
class BackgroundSyncWorkerTest {

    @Test
    fun workerRunsToCompletion(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        val wm = WorkManager.getInstance(context)

        val request = OneTimeWorkRequestBuilder<com.unifiedcomms.sync.BackgroundSyncWorker>().build()
        wm.enqueueUniqueWork("test.bgsync", ExistingWorkPolicy.REPLACE, request).result.get()

        // ponytail: enqueue future resolves on ENQUEUE, not on completion. Poll until
        // the worker reaches a terminal state (SynchronousExecutor runs doWork inline,
        // but its coroutine finishes after enqueue returns).
        var state = wm.getWorkInfoById(request.id).get().state
        var guard = 0
        while (!state.isFinished && guard < 50) {
            Thread.sleep(100)
            state = wm.getWorkInfoById(request.id).get().state
            guard++
        }
        assertEquals(WorkInfo.State.SUCCEEDED, state)
        assertTrue("worker should finish", state.isFinished)
    }
}
