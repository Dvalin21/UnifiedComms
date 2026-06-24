package com.unifiedcomms.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unifiedcomms.sync.SyncForegroundService
import com.unifiedcomms.reminder.ReminderScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            // Start foreground sync service
            val serviceIntent = Intent(context, SyncForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // Reschedule reminders
            val db = com.unifiedcomms.data.db.UnifiedCommsDatabase.getInstance(context)
            val calendarRepo = com.unifiedcomms.data.repository.CalendarRepositoryImpl(
                db.calendarEventDao(),
                db.calendarDao()
            )
            val reminderScheduler = ReminderScheduler(context, calendarRepo)
            reminderScheduler.scheduleReminders("all")
        }
    }
}