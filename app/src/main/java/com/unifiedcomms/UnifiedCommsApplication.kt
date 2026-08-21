package com.unifiedcomms

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.e2ee.ChatCryptoManager
import com.unifiedcomms.data.e2ee.ChatRelayManager
import com.unifiedcomms.data.e2ee.ChatSyncManager
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

        @Volatile
        var chatCrypto: ChatCryptoManager? = null
        @Volatile
        var chatRelay: ChatRelayManager? = null
        @Volatile
        var chatSync: ChatSyncManager? = null
    }

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    lateinit var database: UnifiedCommsDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.initialize(this)
        database = UnifiedCommsDatabase.getInstance(this)

        // Wire E2EE chat components
        chatCrypto = ChatCryptoManager(this)
        chatRelay = ChatRelayManager(this)
        chatRelay?.loadSavedState()
        chatSync = ChatSyncManager(
            context = this,
            relay = chatRelay!!,
            crypto = chatCrypto!!,
            messageDao = database.messageDao(),
        )

        initializeNotificationChannels()
        DemoDataSeeder.seedIfNeeded(this, mainCoroutineScope)
        val intervalMin = com.unifiedcomms.util.PreferencesManager.getInstance().getSyncIntervalMinutes(15).toLong()
        com.unifiedcomms.sync.BackgroundSyncScheduler.schedule(this, intervalMin)
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