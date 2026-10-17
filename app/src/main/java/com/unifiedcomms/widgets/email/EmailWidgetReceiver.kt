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
                    accounts.sumOf { account -> repository.getUnreadCount(account.id, "INBOX") }
                }
                val messages = runBlocking {
                    if (accounts.isEmpty()) emptyList()
                    else repository.getUnifiedInbox(accounts.map { it.id }, listOf("INBOX"), 100).first()
                        .filter { it.isUnread() }
                        .take(5)
                }

                val views = RemoteViews(context.packageName, R.layout.widget_email)
                views.setTextViewText(
                    R.id.widget_email_count,
                    when {
                        accounts.isEmpty() -> "No accounts"
                        unread == 0 -> "Inbox 0 unread"
                        else -> "$unread unread · latest below"
                    }
                )
                val rowIds = intArrayOf(R.id.email_r1_p, R.id.email_r2_p, R.id.email_r3_p, R.id.email_r4_p, R.id.email_r5_p)
                for (idx in 0 until 5) {
                    if (idx < messages.size) {
                        val msg = messages[idx]
                        views.setTextViewText(rowIds[idx], "${msg.sender.email.ifBlank { "?" }} · ${msg.subject.ifBlank { "(no subject)" }}")
                        views.setViewVisibility(rowIds[idx], android.view.View.VISIBLE)
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("navigate_to", "email_detail/${msg.id}")
                        }
                        val rowPending = PendingIntent.getActivity(
                            context, 1000 + idx, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(rowIds[idx], rowPending)
                    } else {
                        views.setTextViewText(rowIds[idx], "")
                        views.setViewVisibility(rowIds[idx], android.view.View.GONE)
                        views.setOnClickPendingIntent(rowIds[idx], null)
                    }
                }

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "email")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
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
