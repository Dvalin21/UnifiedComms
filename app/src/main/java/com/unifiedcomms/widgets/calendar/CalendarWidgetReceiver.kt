package com.unifiedcomms.widgets.calendar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.unifiedcomms.R
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CalendarWidgetReceiver : AppWidgetProvider() {

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
                val repository = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
                val accountIds = runBlocking { db.accountDao().getAllActive().first() }.map { it.id }
                val now = System.currentTimeMillis()
                val events = if (accountIds.isEmpty()) emptyList() else runBlocking {
                    repository.getEventsInRangeUnified(accountIds, now, now + 7L * 86_400_000L).first()
                }.filter { !it.isCancelled }.sortedBy { it.startAtMs }.take(3)

                val views = RemoteViews(context.packageName, R.layout.widget_calendar)
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("navigate_to", "calendar")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                views.setTextViewText(
                    R.id.widget_calendar_date,
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                )
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val zone = ZoneId.systemDefault()
                val text = if (events.isEmpty()) "No events" else events.joinToString("\n") { event ->
                    val time = if (event.isAllDay()) "All day" else Instant.ofEpochMilli(event.startAtMs)
                        .atZone(zone).format(formatter)
                    "$time  ${event.title}"
                }
                views.setTextViewText(R.id.widget_calendar_events, text)
                views.setViewVisibility(R.id.widget_calendar_weather_row, View.GONE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("CalendarWidgetReceiver", "Unable to update widget", e)
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
                android.content.ComponentName(context, CalendarWidgetReceiver::class.java)
            )
            CalendarWidgetReceiver().onUpdate(context, appWidgetManager, ids)
        }
    }
}
