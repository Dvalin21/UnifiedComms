package com.unifiedcomms.data.repository

import com.unifiedcomms.data.model.Calendar
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface CalendarRepository {
    // Events
    suspend fun insertEvent(event: CalendarEvent): Long
    suspend fun insertEvents(events: List<CalendarEvent>): List<Long>
    suspend fun updateEvent(event: CalendarEvent): Int
    suspend fun updateEvents(events: List<CalendarEvent>): Int
    suspend fun deleteEvent(event: CalendarEvent): Int
    suspend fun deleteEventById(id: String): Int
    suspend fun getEventById(id: String): CalendarEvent?
    suspend fun getEventByUid(uid: String, accountId: String): CalendarEvent?
    fun getEventsByCalendar(accountId: String, calendarId: String): Flow<List<CalendarEvent>>
    fun getAllEventsForAccount(accountId: String): Flow<List<CalendarEvent>>
    fun getUnifiedEvents(accountIds: List<String>): Flow<List<CalendarEvent>>
    fun getEventsInRange(accountId: String, start: Long, end: Long): Flow<List<CalendarEvent>>
    fun getEventsInRangeUnified(accountIds: List<String>, start: Long, end: Long): Flow<List<CalendarEvent>>
    fun getEventsForDate(accountId: String, date: Long): Flow<List<CalendarEvent>>
    fun getEventsForDateUnified(accountIds: List<String>, date: Long): Flow<List<CalendarEvent>>
    fun getUpcomingEvents(accountId: String, now: Long, limit: Int): Flow<List<CalendarEvent>>
    fun getUpcomingEventsUnified(accountIds: List<String>, now: Long, limit: Int): Flow<List<CalendarEvent>>
    fun getRecurringEvents(accountId: String): Flow<List<CalendarEvent>>
    fun getOrganizedBy(accountId: String, email: String): Flow<List<CalendarEvent>>
    fun getAttendedBy(accountId: String, email: String): Flow<List<CalendarEvent>>
    fun searchEvents(query: String, accountIds: List<String>, limit: Int): Flow<List<CalendarEvent>>
    suspend fun getEventsNeedingSync(accountId: String): List<CalendarEvent>
    suspend fun getLocalOnlyEvents(accountId: String): List<CalendarEvent>
    suspend fun markEventCancelled(eventUid: String, accountId: String)
    suspend fun markEventSynced(id: String): Int
    suspend fun markAllEventsNeedingSync(accountId: String): Int
    suspend fun cleanupCancelledEvents(accountId: String, olderThan: Long): Int

    // Calendars
    suspend fun insertCalendar(calendar: Calendar): Long
    suspend fun insertCalendars(calendars: List<Calendar>): List<Long>
    suspend fun updateCalendar(calendar: Calendar): Int
    suspend fun deleteCalendar(calendar: Calendar): Int
    suspend fun getCalendarById(id: String): Calendar?
    fun getCalendarsByAccount(accountId: String): Flow<List<Calendar>>
    fun getSelectedCalendarsByAccount(accountId: String): Flow<List<Calendar>>
    fun getSelectedCalendarsUnified(accountIds: List<String>): Flow<List<Calendar>>
    suspend fun getCalendarByServerId(accountId: String, serverId: String): Calendar?
    suspend fun setCalendarSelected(id: String, selected: Boolean): Int
    suspend fun updateLastSynced(accountId: String, now: Long): Int
}