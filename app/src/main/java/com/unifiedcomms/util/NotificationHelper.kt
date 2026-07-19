package com.unifiedcomms.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unifiedcomms.R
import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.datetime.Instant

object NotificationHelper {

    const val CHANNEL_ID_EMAIL = "email"
    const val CHANNEL_ID_CALENDAR = "calendar"
    const val CHANNEL_ID_TASKS = "tasks"
    const val CHANNEL_ID_MESSAGES = "messages"
    const val CHANNEL_ID_REMINDERS = "reminders"
    const val CHANNEL_ID_SYNC = "sync"
    const val CHANNEL_ID_SECURITY = "security"

    private fun Context.notifySafe(id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(id, notification)
        } else {
            notifyBlocked()
        }
    }

    private fun Context.notifySafe(tag: String?, id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(tag, id, notification)
        } else {
            notifyBlocked()
        }
    }

    // ponytail: never silently drop alerts. When POST_NOTIFICATIONS is denied (Android 13+),
    // every email/calendar/task/reminder notification would otherwise vanish with no signal.
    // Instead post one persistent, tapping-routes-to-settings notice so the user can re-grant.
    private fun Context.notifyBlocked() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "settings")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 7777, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SECURITY)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle("Notifications are blocked")
            .setContentText("UnifiedComms can't alert you to new mail, events, or tasks. Tap to enable.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(7777, notification)
    }

    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(CHANNEL_ID_EMAIL, "Email", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New email notifications"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                    lightColor = Color.BLUE
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(CHANNEL_ID_CALENDAR, "Calendar", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Calendar event reminders"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 500, 500)
                    lightColor = Color.GREEN
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(CHANNEL_ID_TASKS, "Tasks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Task reminders and due dates"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 300, 300)
                    lightColor = Color.YELLOW
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(CHANNEL_ID_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New messages and chat notifications"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 200, 200)
                    lightColor = Color.MAGENTA
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    CHANNEL_ID_REMINDERS,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Full-screen calendar reminders"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                    lightColor = Color.RED
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_ID_SYNC, "Sync", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Background sync status"
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                },
                NotificationChannel(CHANNEL_ID_SECURITY, "Security", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Security alerts and authentication"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 500)
                    lightColor = Color.parseColor("#FF5722")
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )

            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    fun showEmailNotification(
        context: Context,
        accountId: String,
        accountName: String,
        sender: String,
        subject: String,
        preview: String,
        messageId: String,
        unreadCount: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "email/$accountId/INBOX")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, messageId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_EMAIL)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle("$accountName: $sender")
            .setContentText(subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setNumber(unreadCount)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("email_$accountId")
            .setGroupSummary(false)
            .build()

        context.notifySafe("email_$messageId".hashCode(), notification)
    }

    fun showCalendarReminder(
        context: Context,
        eventId: String,
        title: String,
        time: String,
        location: String?
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "event_detail/$eventId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val fullScreenIntent = Intent(context, com.unifiedcomms.reminder.FullScreenReminderActivity::class.java).apply {
            putExtra("event_id", eventId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, eventId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pendingIntent = PendingIntent.getActivity(
            context, eventId.hashCode() + 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("Event Reminder: $title")
            .setContentText("$time${location?.let { " @ $it" } ?: ""}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        context.notifySafe(eventId.hashCode(), notification)
    }

    fun showTaskReminder(
        context: Context,
        taskId: String,
        title: String,
        dueTime: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "task_detail/$taskId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TASKS)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle("Task Due: $title")
            .setContentText("Due $dueTime")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        context.notifySafe(taskId.hashCode(), notification)
    }

    fun showMessageNotification(
        context: Context,
        conversationId: String,
        senderName: String,
        message: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "conversation/$conversationId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, conversationId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle(senderName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("messages")
            .setGroupSummary(false)
            .build()

        context.notifySafe(conversationId.hashCode(), notification)
    }

    fun showCalendarInviteNotification(
        context: Context,
        eventId: String,
        title: String,
        organizer: String,
        time: String,
        location: String?
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "event_detail/$eventId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val acceptIntent = Intent(context, InviteActionReceiver::class.java).apply {
            putExtra("event_id", eventId)
            putExtra("response", AttendeeStatus.ACCEPTED.ordinal)
        }
        val declineIntent = Intent(context, InviteActionReceiver::class.java).apply {
            putExtra("event_id", eventId)
            putExtra("response", AttendeeStatus.DECLINED.ordinal)
        }
        val tentativeIntent = Intent(context, InviteActionReceiver::class.java).apply {
            putExtra("event_id", eventId)
            putExtra("response", AttendeeStatus.TENTATIVE.ordinal)
        }

        val acceptPending = PendingIntent.getBroadcast(
            context, eventId.hashCode(), acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val declinePending = PendingIntent.getBroadcast(
            context, eventId.hashCode() + 1, declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val tentativePending = PendingIntent.getBroadcast(
            context, eventId.hashCode() + 2, tentativeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CALENDAR)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("Calendar Invite: $title")
            .setContentText("From $organizer at $time${location?.let { " @ $it" } ?: ""}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(PendingIntent.getActivity(
                context, eventId.hashCode() + 3, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))
            .addAction(NotificationCompat.Action.Builder(
                null, "Yes", acceptPending
            ).build())
            .addAction(NotificationCompat.Action.Builder(
                null, "No", declinePending
            ).build())
            .addAction(NotificationCompat.Action.Builder(
                null, "Maybe", tentativePending
            ).build())
            .setAutoCancel(true)
            .build()

        context.notifySafe(eventId.hashCode() + 100, notification)
    }

    fun showSyncNotification(
        context: Context,
        message: String,
        progress: Int = -1
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle("UnifiedComms Sync")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        }

        context.notifySafe(1001, builder.build())
    }

    fun dismissSyncNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(1001)
    }

    fun showSecurityNotification(
        context: Context,
        title: String,
        message: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "settings")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SECURITY)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        context.notifySafe(2001, notification)
    }
}