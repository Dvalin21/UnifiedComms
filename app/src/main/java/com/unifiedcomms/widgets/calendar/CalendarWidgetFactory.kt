package com.unifiedcomms.widgets.calendar

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class CalendarWidgetFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var eventTitles: List<String> = emptyList()
    private var eventTimes: List<String> = emptyList()

    override fun onCreate() {
        loadEvents()
    }

    override fun onDataSetChanged() {
        loadEvents()
    }

    private fun loadEvents() {
        try {
            val prefs = context.getSharedPreferences("uc_calendar_widget", Context.MODE_PRIVATE)
            val titles = mutableListOf<String>()
            val times = mutableListOf<String>()
            for (i in 1..10) {
                val title = prefs.getString("event_${i}_title", null)
                val time = prefs.getString("event_${i}_time", null)
                if (title != null && time != null) {
                    titles.add(title)
                    times.add(time)
                } else {
                    break
                }
            }
            eventTitles = titles
            eventTimes = times
        } catch (e: Exception) {
            eventTitles = emptyList()
            eventTimes = emptyList()
        }
    }

    override fun getCount(): Int = eventTitles.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, com.unifiedcomms.R.layout.widget_calendar_item)
        val title = eventTitles.getOrElse(position) { "" }
        val time = eventTimes.getOrElse(position) { "" }
        views.setTextViewText(com.unifiedcomms.R.id.widget_calendar_item_title, title)
        views.setTextViewText(com.unifiedcomms.R.id.widget_calendar_item_time, time)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() {}
}
