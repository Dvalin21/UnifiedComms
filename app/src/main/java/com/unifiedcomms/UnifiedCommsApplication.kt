package com.unifiedcomms

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    }

    private fun initializeNotificationChannels() {
        // Notification channels will be created by NotificationManager
    }

    val mainCoroutineScope: CoroutineScope get() = mainScope
    val ioCoroutineScope: CoroutineScope get() = ioScope

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        INSTANCE = this
    }
}