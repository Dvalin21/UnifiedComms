package com.unifiedcomms.reminder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unifiedcomms.R
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.ReminderMethod
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.db.dao.CalendarEventDao
import com.unifiedcomms.data.db.dao.CalendarDao
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.util.PreferencesManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.TimeZone

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = runCatching { PreferencesManager.getInstance() }.getOrNull()
        val eventId = intent.getStringExtra("event_id") ?: return
        val accountId = intent.getStringExtra("account_id") ?: return
        if (prefs?.getBoolean("notif_calendar", true) == false) {
            ReminderScheduler.forgetScheduledKey(intent.getStringExtra("reminder_key"))
            return
        }
        val occurrenceStart = intent.getLongExtra("occurrence_start", -1L)
        val fullScreen = prefs?.getBoolean("notif_fullscreen", true) != false
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.forgetScheduledKey(intent.getStringExtra("reminder_key"))
                val account = runCatching {
                    UnifiedCommsDatabase.getInstance(context).accountDao().getById(accountId)
                }.getOrNull()
                if (account?.isActive != true) return@launch

                withContext(Dispatchers.Main) {
                    // Android 8+ owns background activity launches. A full-screen
                    // notification is the supported path; direct launch remains only
                    // for pre-O devices where receivers could start activities directly.
                    if (fullScreen && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        val launchIntent = Intent(context, FullScreenReminderActivity::class.java).apply {
                            putExtra("event_id", eventId)
                            putExtra("account_id", accountId)
                            if (occurrenceStart >= 0L) putExtra("occurrence_start", occurrenceStart)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(launchIntent)
                    }

                    showNotification(context, eventId, accountId, occurrenceStart, fullScreen)
                }
            } finally {
                pending.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(
        context: Context,
        eventId: String,
        accountId: String,
        occurrenceStart: Long,
        fullScreen: Boolean
    ) {
        val reminderIntent = Intent(context, FullScreenReminderActivity::class.java).apply {
            putExtra("event_id", eventId)
            putExtra("account_id", accountId)
            if (occurrenceStart >= 0L) putExtra("occurrence_start", occurrenceStart)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val notificationId = eventId.substringBefore('#').hashCode()
        val reminderPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            reminderIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("Event Reminder")
            .setContentText("Tap to view event")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(reminderPendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)

        if (fullScreen && canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(reminderPendingIntent, true)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    private fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager?.canUseFullScreenIntent() == true
    }
}

class FullScreenReminderActivity : Activity() {

    private lateinit var calendarRepo: CalendarRepository
    private var notificationId: Int? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dependencies manually since Hilt is disabled
        val db = UnifiedCommsDatabase.getInstance(this)
        val calendarDao = db.calendarEventDao()
        val calDao = db.calendarDao()
        calendarRepo = CalendarRepositoryImpl(calendarDao, calDao)

        // Full-screen, over lock screen (API 27+ flags; deprecated flags suppressed on older paths)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContentView(R.layout.activity_fullscreen_reminder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        val eventId = intent.getStringExtra("event_id") ?: run { finish(); return }
        notificationId = eventId.substringBefore('#').hashCode()
        val accountId = intent.getStringExtra("account_id") ?: run { finish(); return }
        val occurrenceStart = intent.getLongExtra("occurrence_start", -1L)

        // Resolve an expanded recurrence by its occurrence start. Older alarms may
        // only carry the synthetic id, so fall back to the persisted master row.
        activityScope.launch {
            val event = withContext(Dispatchers.IO) {
                val masterId = eventId.substringBefore('#')
                if (occurrenceStart >= 0L) {
                    calendarRepo.getEventsInRange(accountId, occurrenceStart - 1L, occurrenceStart + 1L)
                        .first()
                        .firstOrNull {
                            it.startAt.toInstant().toEpochMilliseconds() == occurrenceStart &&
                                (it.id == eventId || it.id == masterId)
                        }
                } else null
                    ?: calendarRepo.getEventById(masterId)
                    ?: calendarRepo.getEventById(eventId)
            }
            if (event == null) finish() else populateUI(event)
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
            cancelNotification()
            finish()
        }
        findViewById<android.widget.Button>(R.id.btn_view).setOnClickListener {
            openEventDetail(event)
        }
    }

    private fun cancelNotification() {
        notificationId?.let { getSystemService(NotificationManager::class.java).cancel(it) }
    }

    private fun snoozeReminder(event: CalendarEvent) {
        // Reschedule for 5 minutes
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val key = reminderKey(event, 5)
        val intent = Intent(this, ReminderAlarmReceiver::class.java).apply {
            putExtra("event_id", event.id)
            putExtra("account_id", event.accountId)
            putExtra("occurrence_start", event.startAt.toInstant().toEpochMilliseconds())
            putExtra("reminder_key", key)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val triggerTime = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes
        // ponytail: guard exact-alarm on API 31+. Without SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM
        // (revoked on 12+ in battery saver), setExactAndAllowWhileIdle throws SecurityException.
        // Fall back to an inexact set() so the snooze still fires instead of crashing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
        ReminderScheduler.rememberScheduledKey(key)

        cancelNotification()
        finish()
    }

    // ponytail: FullScreenReminderActivity is a plain Activity that only reads
    // event_id/account_id — it never interprets navigate_to, so relaunching it just
    // re-shows the reminder (a dead View button). Route to MainActivity, which owns
    // the event_detail route, via the same navigate_to extra the rest of the app uses.
    private fun openEventDetail(event: CalendarEvent) {
        val intent = Intent(this, com.unifiedcomms.ui.main.MainActivity::class.java).apply {
            putExtra("navigate_to", "event_detail/${event.id.substringBefore('#')}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        cancelNotification()
        finish()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}

internal fun reminderKey(event: CalendarEvent, minutesBefore: Int): String =
    listOf(
        event.accountId,
        event.id.substringBefore('#'),
        event.startAt.toInstant().toEpochMilliseconds().toString(),
        minutesBefore.toString()
    ).joinToString("|")

class ReminderScheduler(
    private val context: Context,
    private val calendarRepo: CalendarRepository,
    private val accountRepo: AccountRepository? = null
) {

    companion object {
        internal const val SCHEDULED_KEYS = "reminder_alarm_keys"
        internal val keyLock = Any()

        internal fun rememberScheduledKey(key: String) {
            synchronized(keyLock) {
                val prefs = runCatching { PreferencesManager.getInstance() }.getOrNull() ?: return
                prefs.putStringSet(SCHEDULED_KEYS, prefs.getStringSet(SCHEDULED_KEYS).orEmpty() + key)
            }
        }

        internal fun forgetScheduledKey(key: String?) {
            if (key.isNullOrBlank()) return
            synchronized(keyLock) {
                val prefs = runCatching { PreferencesManager.getInstance() }.getOrNull() ?: return
                prefs.putStringSet(SCHEDULED_KEYS, prefs.getStringSet(SCHEDULED_KEYS).orEmpty() - key)
            }
        }
    }

    fun scheduleReminders(accountId: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val activeCalendarAccounts = accountRepo?.getAllActive()?.firstOrNull()
                ?.filter { it.isActive }
                .orEmpty()
            val accountIds = when {
                accountId == null || accountId == "all" -> activeCalendarAccounts.map { it.id }
                accountRepo == null -> listOf(accountId)
                else -> activeCalendarAccounts.filter { it.id == accountId }.map { it.id }
            }
            accountIds.forEach { id ->
                val upcomingEvents = calendarRepo.getUpcomingEvents(id, System.currentTimeMillis(), 50).first()
                upcomingEvents.forEach { event -> scheduleEventReminder(event) }
            }
        }
    }

    private fun scheduleEventReminder(event: CalendarEvent) {
        if (runCatching { PreferencesManager.getInstance().getBoolean("notif_calendar", true) }.getOrDefault(true).not()) return
        event.reminders.forEach { reminder ->
            val triggerTime = event.startAt.toInstant(TimeZone.of(event.startAt.timeZone)).toEpochMilliseconds() - (reminder.minutesBefore * 60 * 1000)

            if (triggerTime > System.currentTimeMillis()) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val key = reminderKey(event, reminder.minutesBefore)
                val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                    putExtra("event_id", event.id)
                    putExtra("account_id", event.accountId)
                    putExtra("occurrence_start", event.startAt.toInstant().toEpochMilliseconds())
                    putExtra("reminder_key", reminderKey(event, reminder.minutesBefore))
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    key.hashCode(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    // ponytail: exact-alarm revoked on 12+ → inexact set() instead of crashing.
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
                rememberScheduledKey(key)
            }
        }
    }

    fun cancelReminders(eventId: String) {
        val masterId = eventId.substringBefore('#')
        cancelKeys { parts -> parts.size == 4 && parts[1] == masterId }
    }

    fun cancelAccountReminders(accountId: String) {
        cancelKeys { parts -> parts.size == 4 && parts[0] == accountId }
    }

    fun cancelAll() = cancelKeys { true }

    private fun cancelKeys(predicate: (List<String>) -> Boolean) {
        val keys = synchronized(keyLock) {
            val prefs = PreferencesManager.getInstance()
            prefs.getStringSet(SCHEDULED_KEYS).orEmpty()
                .filter { predicate(it.split('|', limit = 4)) }
                .toSet()
        }
        if (keys.isEmpty()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        keys.forEach { key ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                key.hashCode(),
                Intent(context, ReminderAlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pendingIntent)
            key.split('|', limit = 4).getOrNull(1)?.let { masterId ->
                context.getSystemService(NotificationManager::class.java).cancel(masterId.hashCode())
            }
        }
        synchronized(keyLock) {
            val prefs = PreferencesManager.getInstance()
            prefs.putStringSet(SCHEDULED_KEYS, prefs.getStringSet(SCHEDULED_KEYS).orEmpty() - keys)
        }
    }

}