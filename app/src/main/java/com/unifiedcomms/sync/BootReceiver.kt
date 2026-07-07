package com.unifiedcomms.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unifiedcomms.reminder.ReminderScheduler
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.repository.CalendarRepositoryImpl

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            // Reschedule reminders
            val db = UnifiedCommsDatabase.getInstance(context)
            val calendarRepo = CalendarRepositoryImpl(
                db.calendarEventDao(),
                db.calendarDao()
            )
            val reminderScheduler = ReminderScheduler(context, calendarRepo)
            reminderScheduler.scheduleReminders("all")
        }
    }
}
