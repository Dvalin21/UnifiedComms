package com.unifiedcomms.reminder

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.unifiedcomms.R
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.ReminderMethod
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.db.dao.CalendarEventDao
import com.unifiedcomms.data.db.dao.CalendarDao
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("event_id") ?: return
        val accountId = intent.getStringExtra("account_id") ?: return

        // Launch full-screen reminder activity
        val launchIntent = Intent(context, FullScreenReminderActivity::class.java).apply {
            putExtra("event_id", eventId)
            putExtra("account_id", accountId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        // Turn on screen and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            context.startForegroundService(launchIntent)
        } else {
            context.startActivity(launchIntent)
        }

        // Also show notification as backup
        showNotification(context, eventId)
    }

    private fun showNotification(context: Context, eventId: String) {
        val notification = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("Event Reminder")
            .setContentText("Tap to view event")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setFullScreenIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, FullScreenReminderActivity::class.java).putExtra("event_id", eventId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ),
                true
            )
            .build()

        NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
    }
}

class FullScreenReminderActivity : Activity() {

    private lateinit var calendarRepo: CalendarRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dependencies manually since Hilt is disabled
        val db = UnifiedCommsDatabase.getInstance(this)
        val calendarDao = db.calendarEventDao()
        val calDao = db.calendarDao()
        calendarRepo = CalendarRepositoryImpl(calendarDao, calDao)

        // Full-screen, over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_fullscreen_reminder)

        val eventId = intent.getStringExtra("event_id") ?: return
        val accountId = intent.getStringExtra("account_id") ?: return

        // Load event and populate UI
        CoroutineScope(Dispatchers.IO).launch {
            val event = calendarRepo.getEventById(eventId)
            runOnUiThread {
                event?.let { populateUI(it) }
            }
        }
    }

    private fun populateUI(event: CalendarEvent) {
        findViewById<android.widget.TextView>(R.id.tv_event_title).text = event.title
        findViewById<android.widget.TextView>(R.id.tv_event_time).text =
            "${event.startAt.toInstant().toString()} - ${event.endAt.toInstant().toString()}"
        findViewById<android.widget.TextView>(R.id.tv_event_location).text = event.location ?: ""
        findViewById<android.widget.TextView>(R.id.tv_event_description).text = event.description ?: ""

        // Color the background
        val color = event.getColorInt()
        findViewById<android.view.View>(R.id.reminder_background).setBackgroundColor(color)

        // Buttons
        findViewById<android.widget.Button>(R.id.btn_snooze).setOnClickListener {
            snoozeReminder(event)
        }
        findViewById<android.widget.Button>(R.id.btn_dismiss).setOnClickListener {
            finish()
        }
        findViewById<android.widget.Button>(R.id.btn_view).setOnClickListener {
            openEventDetail(event)
        }
    }

    private fun snoozeReminder(event: CalendarEvent) {
        // Reschedule for 5 minutes
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderAlarmReceiver::class.java).apply {
            putExtra("event_id", event.id)
            putExtra("account_id", event.accountId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val triggerTime = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        finish()
    }

    private fun openEventDetail(event: CalendarEvent) {
        val intent = Intent(this, FullScreenReminderActivity::class.java).apply {
            putExtra("navigate_to", "event_detail/${event.id}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }
}

class ReminderScheduler(
    private val context: Context,
    private val calendarRepo: CalendarRepository
) {

    fun scheduleReminders(accountId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val upcomingEvents = calendarRepo.getUpcomingEvents(accountId, System.currentTimeMillis(), 50).first()
            upcomingEvents.forEach { event ->
                scheduleEventReminder(event)
            }
        }
    }

    private fun scheduleEventReminder(event: CalendarEvent) {
        event.reminders.forEach { reminder ->
            val triggerTime = event.startAt.toInstant().toEpochMilliseconds() - (reminder.minutesBefore * 60 * 1000)

            if (triggerTime > System.currentTimeMillis()) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                    putExtra("event_id", event.id)
                    putExtra("account_id", event.accountId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    "${event.id}_${reminder.minutesBefore}".hashCode(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            }
        }
    }

    fun cancelReminders(eventId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancel all reminders for this event
        // Would need to track pending intent IDs
    }
}