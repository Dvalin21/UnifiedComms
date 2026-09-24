package com.unifiedcomms.widgets.tasks

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
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TasksWidgetReceiver : AppWidgetProvider() {

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
                val repository = TaskRepositoryImpl(db.taskDao(), db.taskListDao())
                val accountIds = runBlocking { db.accountDao().getAllActive().first() }.map { it.id }
                val tasks = if (accountIds.isEmpty()) emptyList() else runBlocking {
                    repository.getActiveUnified(accountIds, TaskStatus.COMPLETED).first()
                }.sortedBy { it.dueAtMs }.take(3)

                val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("navigate_to", "tasks")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                views.setTextViewText(R.id.widget_tasks_due, "Next tasks")
                views.setTextViewText(
                    R.id.widget_tasks_text,
                    if (tasks.isEmpty()) "No tasks" else tasks.joinToString("\n") { task ->
                        val due = task.dueAt?.date?.toString()
                            ?: task.dueAt?.dateTime?.date?.toString()
                            ?: ""
                        if (due.isBlank()) task.title else "${task.title} · $due"
                    }
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("TasksWidgetReceiver", "Unable to update widget", e)
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
                android.content.ComponentName(context, TasksWidgetReceiver::class.java)
            )
            TasksWidgetReceiver().onUpdate(context, appWidgetManager, ids)
        }
    }
}
