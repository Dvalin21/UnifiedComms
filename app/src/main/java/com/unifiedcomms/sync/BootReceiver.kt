package com.unifiedcomms.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unifiedcomms.reminder.ReminderScheduler
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            val db = UnifiedCommsDatabase.getInstance(context)
            val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
            val accountRepo = AccountRepositoryImpl(db.accountDao(), CryptoManagerImpl(context))
            // ponytail: "all" now resolves to every real account id (was a no-op before).
            val reminderScheduler = ReminderScheduler(context, calendarRepo, accountRepo)
            reminderScheduler.scheduleReminders(null)
        }
    }
}
