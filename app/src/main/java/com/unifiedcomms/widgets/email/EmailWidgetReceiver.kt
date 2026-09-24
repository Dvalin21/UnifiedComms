package com.unifiedcomms.widgets.email

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.unifiedcomms.R
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class EmailWidgetReceiver : AppWidgetProvider() {

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
                val repository = EmailRepositoryImpl(db.emailDao())
                val accounts = runBlocking { db.accountDao().getAllActive().first() }
                val unread = runBlocking {
                    accounts.sumOf { account ->
                        listOf("INBOX", "Inbox", "inbox")
                            .map { repository.getUnreadCount(account.id, it) }
                            .firstOrNull { count -> count > 0 } ?: 0
                    }
                }
                val views = RemoteViews(context.packageName, R.layout.widget_email)
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("navigate_to", "email")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                views.setTextViewText(R.id.widget_email_count, unread.toString())
                views.setTextViewText(
                    R.id.widget_email_account,
                    accounts.firstOrNull()?.name ?: "No account"
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("EmailWidgetReceiver", "Unable to update widget", e)
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
                android.content.ComponentName(context, EmailWidgetReceiver::class.java)
            )
            EmailWidgetReceiver().onUpdate(context, appWidgetManager, ids)
        }
    }
}
