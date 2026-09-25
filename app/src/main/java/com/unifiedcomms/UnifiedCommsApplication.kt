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
        // ponytail: no plaintext attachment bytes survive a process start. The store also
        // clears the pre-2026-09 cacheDir/attachments directory on this first run.
        com.unifiedcomms.security.AttachmentStore.forApp(cacheDir).purge()
        database = UnifiedCommsDatabase.getInstance(this)

        initializeNotificationChannels()
        DemoDataSeeder.seedIfNeeded(this, mainCoroutineScope)
        val prefs = PreferencesManager.getInstance()
        com.unifiedcomms.sync.BackgroundSyncScheduler.schedule(
            this,
            prefs.getSyncIntervalMinutes(15).toLong(),
            autoSync = prefs.getBoolean("auto_sync", true),
            wifiOnly = prefs.getBoolean("sync_wifi_only", false)
        )
    }

    private fun initializeNotificationChannels() {
        NotificationHelper.createNotificationChannels(this)
    }

    val mainCoroutineScope: CoroutineScope get() = mainScope
    val ioCoroutineScope: CoroutineScope get() = ioScope

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        INSTANCE = this
    }
}