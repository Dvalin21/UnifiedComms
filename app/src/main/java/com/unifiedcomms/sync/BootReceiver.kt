package com.unifiedcomms.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unifiedcomms.sync.SyncForegroundService
import com.unifiedcomms.reminder.ReminderScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            // Start foreground sync service
            val serviceIntent = Intent(context, SyncForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // Reschedule reminders
            val reminderScheduler = ReminderScheduler(
                context,
                // Would inject calendar repo in real implementation
                object : com.unifiedcomms.data.repository.CalendarRepository {
                    override suspend fun insertEvent(event: com.unifiedcomms.data.model.CalendarEvent): Long = 0
                    override suspend fun insertEvents(events: List<com.unifiedcomms.data.model.CalendarEvent>): List<Long> = emptyList()
                    override suspend fun updateEvent(event: com.unifiedcomms.data.model.CalendarEvent): Int = 0
                    override suspend fun updateEvents(events: List<com.unifiedcomms.data.model.CalendarEvent>): Int = 0
                    override suspend fun deleteEvent(event: com.unifiedcomms.data.model.CalendarEvent): Int = 0
                    override suspend fun deleteEventById(id: String): Int = 0
                    override suspend fun getEventById(id: String): com.unifiedcomms.data.model.CalendarEvent? = null
                    override suspend fun getEventByUid(uid: String, accountId: String): com.unifiedcomms.data.model.CalendarEvent? = null
                    override fun getEventsByCalendar(accountId: String, calendarId: String): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getAllEventsForAccount(accountId: String): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getUnifiedEvents(accountIds: List<String>): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getEventsInRange(accountId: String, start: Long, end: Long): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getEventsInRangeUnified(accountIds: List<String>, start: Long, end: Long): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getEventsForDate(accountId: String, date: Long): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getEventsForDateUnified(accountIds: List<String>, date: Long): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getUpcomingEvents(accountId: String, now: Long, limit: Int): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getUpcomingEventsUnified(accountIds: List<String>, now: Long, limit: Int): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getRecurringEvents(accountId: String): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getOrganizedBy(accountId: String, email: String): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getAttendedBy(accountId: String, email: String): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun searchEvents(query: String, accountIds: List<String>, limit: Int): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> = kotlinx.coroutines.flow.emptyFlow()
                    override suspend fun getEventsNeedingSync(accountId: String): List<com.unifiedcomms.data.model.CalendarEvent> = emptyList()
                    override suspend fun getLocalOnlyEvents(accountId: String): List<com.unifiedcomms.data.model.CalendarEvent> = emptyList()
                    override suspend fun markEventCancelled(eventUid: String, accountId: String) {}
                    override suspend fun markEventSynced(id: String): Int = 0
                    override suspend fun markAllEventsNeedingSync(accountId: String): Int = 0
                    override suspend fun cleanupCancelledEvents(accountId: String, olderThan: Long): Int = 0
                    override suspend fun insertCalendar(calendar: com.unifiedcomms.data.model.Calendar): Long = 0
                    override suspend fun insertCalendars(calendars: List<com.unifiedcomms.data.model.Calendar>): List<Long> = emptyList()
                    override suspend fun updateCalendar(calendar: com.unifiedcomms.data.model.Calendar): Int = 0
                    override suspend fun deleteCalendar(calendar: com.unifiedcomms.data.model.Calendar): Int = 0
                    override suspend fun getCalendarById(id: String): com.unifiedcomms.data.model.Calendar? = null
                    override fun getCalendarsByAccount(accountId: String): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.Calendar>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getSelectedCalendarsByAccount(accountId: String): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.Calendar>> = kotlinx.coroutines.flow.emptyFlow()
                    override fun getSelectedCalendarsUnified(accountIds: List<String>): kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.Calendar>> = kotlinx.coroutines.flow.emptyFlow()
                    override suspend fun getCalendarByServerId(accountId: String, serverId: String): com.unifiedcomms.data.model.Calendar? = null
                    override suspend fun setCalendarSelected(id: String, selected: Boolean): Int = 0
                    override suspend fun updateLastSynced(accountId: String, now: Long): Int = 0
                }
            )
            reminderScheduler.scheduleReminders("all")
        }
    }
}