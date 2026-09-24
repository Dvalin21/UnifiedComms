package com.unifiedcomms.widgets.unified

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.unifiedcomms.R
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class UnifiedWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val pending = goAsync()
        Thread {
            try {
                val db = UnifiedCommsDatabase.getInstance(context)
                val accounts = runBlocking { db.accountDao().getAllActive().first() }
                val accountIds = accounts.map { it.id }
                val emailRepository = EmailRepositoryImpl(db.emailDao())
                val calendarRepository = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
                val taskRepository = TaskRepositoryImpl(db.taskDao(), db.taskListDao())
                val now = System.currentTimeMillis()
                val unread = runBlocking {
                    accounts.sumOf { account ->
                        listOf("INBOX", "Inbox", "inbox")
                            .map { emailRepository.getUnreadCount(account.id, it) }
                            .firstOrNull { it > 0 } ?: 0
                    }
                }
                val events = if (accountIds.isEmpty()) emptyList() else runBlocking {
                    calendarRepository.getEventsInRangeUnified(accountIds, now, now + 7L * 86_400_000L).first()
                }.filter { !it.isCancelled }.sortedBy { it.startAtMs }.take(1)
                val tasks = if (accountIds.isEmpty()) emptyList() else runBlocking {
                    taskRepository.getActiveUnified(accountIds, TaskStatus.COMPLETED).first()
                }.sortedBy { it.dueAtMs }.take(1)

                val views = RemoteViews(context.packageName, R.layout.widget_unified)
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("navigate_to", "inbox")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                views.setTextViewText(R.id.widget_unified_date, "Today")
                views.setTextViewText(
                    R.id.widget_unified_calendar,
                    events.firstOrNull()?.title ?: "No events"
                )
                views.setTextViewText(
                    R.id.widget_unified_tasks,
                    tasks.firstOrNull()?.title ?: "No tasks"
                )
                views.setTextViewText(R.id.widget_unified_unread, unread.toString())
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("UnifiedWidgetReceiver", "Unable to update widget", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        @JvmStatic
        fun refreshAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, UnifiedWidgetReceiver::class.java)
            )
            UnifiedWidgetReceiver().onUpdate(context, appWidgetManager, ids)
        }
    }
}
