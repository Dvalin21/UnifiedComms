package com.unifiedcomms.sync

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.unifiedcomms.R
import com.unifiedcomms.ui.main.MainActivity
import com.unifiedcomms.util.NotificationHelper
import com.unifiedcomms.sync.SyncManager
import com.unifiedcomms.sync.EmailSyncEngine
import com.unifiedcomms.sync.EmailSyncEngineImpl
import com.unifiedcomms.sync.CalendarSyncEngine
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.sync.TaskSyncEngine
import com.unifiedcomms.sync.TaskSyncEngineImpl
import com.unifiedcomms.sync.ContactSyncEngine
import com.unifiedcomms.sync.ContactSyncEngineImpl
import com.unifiedcomms.data.repository.*
import com.unifiedcomms.data.db.dao.*
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncForegroundService : Service() {

    private lateinit var syncManager: SyncManager

    private val notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        // Initialize dependencies manually since Hilt is disabled
        val db = UnifiedCommsDatabase.getInstance(this)
        val emailDao = db.emailDao()
        val calendarEventDao = db.calendarEventDao()
        val calendarDao = db.calendarDao()
        val taskDao = db.taskDao()
        val taskListDao = db.taskListDao()
        val contactDao = db.contactDao()

        val emailRepo = EmailRepositoryImpl(emailDao)
        val calendarRepo = CalendarRepositoryImpl(calendarEventDao, calendarDao)
        val taskRepo = TaskRepositoryImpl(taskDao, taskListDao)
        val contactRepo = ContactRepositoryImpl(contactDao)

        val emailSync = EmailSyncEngineImpl(emailRepo, null, null)
        val calendarSync = CalendarSyncEngineImpl(calendarRepo, null, null)
        val taskSync = TaskSyncEngineImpl(taskRepo, null, null)
        val contactSync = ContactSyncEngineImpl(contactRepo, null, null)

        syncManager = SyncManager(emailSync, calendarSync, taskSync, contactSync)

        NotificationHelper.createNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(notificationId, notification)

        // Perform sync for all accounts
        CoroutineScope(Dispatchers.IO).launch {
            NotificationHelper.showSyncNotification(this@SyncForegroundService, "Starting sync...", 0)
            syncManager.syncAllAccounts()
            syncManager.syncProgress.onEach { progress ->
                if (progress == 100) {
                    NotificationHelper.showSyncNotification(this@SyncForegroundService, "Sync completed", 100)
                } else {
                    NotificationHelper.showSyncNotification(this@SyncForegroundService, "Syncing... $progress%", progress)
                }
            }.launchIn(this@SyncForegroundService)

            // Stop after a delay to show completion
            kotlinx.coroutines.delay(3000)
            NotificationHelper.dismissSyncNotification(this@SyncForegroundService)
            stopForeground(true)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_SYNC)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle("UnifiedComms Sync")
            .setContentText("Syncing accounts...")
            .setProgress(100, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }
}