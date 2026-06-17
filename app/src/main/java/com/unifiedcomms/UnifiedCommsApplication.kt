package com.unifiedcomms

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// @HiltAndroidApp
class UnifiedCommsApplication : Application() {

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Initialize database
        UnifiedCommsDatabase.getInstance(this)
        // Initialize WorkManager, notification channels, etc.
        initializeNotificationChannels()
    }

    private fun initializeNotificationChannels() {
        // Notification channels will be created by NotificationManager
    }

    val mainCoroutineScope: CoroutineScope get() = mainScope
    val ioCoroutineScope: CoroutineScope get() = ioScope

    companion object {
        private var instance: UnifiedCommsApplication? = null

        fun getInstance(): UnifiedCommsApplication = instance!!

        private fun setInstance(app: UnifiedCommsApplication) {
            instance = app
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        setInstance(this)
    }
}