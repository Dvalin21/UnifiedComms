package com.unifiedcomms

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.util.PreferencesManager
import com.unifiedcomms.util.DemoDataSeeder
import com.unifiedcomms.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// @HiltAndroidApp
class UnifiedCommsApplication : Application() {

    companion object {
        @Volatile
        private var INSTANCE: UnifiedCommsApplication? = null

        fun getInstance(): UnifiedCommsApplication = INSTANCE!!
    }

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    lateinit var database: UnifiedCommsDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.initialize(this)
        database = UnifiedCommsDatabase.getInstance(this)
        initializeNotificationChannels()
        DemoDataSeeder.seedIfNeeded(this, mainCoroutineScope)
        // Phase 15: schedule background periodic sync (survives process death).
        com.unifiedcomms.sync.BackgroundSyncScheduler.schedule(this)
    }

    private fun initializeNotificationChannels() {
        // ponytail: was an EMPTY stub — createNotificationChannels() (the real
        // channel registration) was dead, so on API 26+ notifications posted to
        // channels that were never created and were dropped. Wire it.
        NotificationHelper.createNotificationChannels(this)
    }

    val mainCoroutineScope: CoroutineScope get() = mainScope
    val ioCoroutineScope: CoroutineScope get() = ioScope

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        INSTANCE = this
    }
}