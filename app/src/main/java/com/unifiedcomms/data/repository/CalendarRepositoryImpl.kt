package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.CalendarDao
import com.unifiedcomms.data.db.dao.CalendarEventDao
import com.unifiedcomms.data.model.Calendar
import com.unifiedcomms.data.model.CalendarEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class CalendarRepositoryImpl(
    private val eventDao: CalendarEventDao,
    private val calDao: CalendarDao
) : CalendarRepository {
    // Events
    override suspend fun insertEvent(event: CalendarEvent): Long = eventDao.insert(event)

    override suspend fun insertEvents(events: List<CalendarEvent>): List<Long> = eventDao.insertAll(events)

    override suspend fun updateEvent(event: CalendarEvent): Int = eventDao.update(event)

    override suspend fun updateEvents(events: List<CalendarEvent>): Int = eventDao.updateAll(events)

    override suspend fun deleteEvent(event: CalendarEvent): Int = eventDao.delete(event)

    override suspend fun deleteEventById(id: String): Int = eventDao.deleteById(id)

    override suspend fun getEventById(id: String): CalendarEvent? = eventDao.getById(id)

    override suspend fun getEventByUid(uid: String, accountId: String): CalendarEvent? = eventDao.getByUid(uid, accountId)

    override fun getEventsByCalendar(accountId: String, calendarId: String): Flow<List<CalendarEvent>> =
        eventDao.getByCalendar(accountId, calendarId)

    override fun getAllEventsForAccount(accountId: String): Flow<List<CalendarEvent>> =
        eventDao.getAllForAccount(accountId)

    override fun getUnifiedEvents(accountIds: List<String>): Flow<List<CalendarEvent>> =
        eventDao.getUnifiedCalendar(accountIds)

    override fun getEventsInRange(accountId: String, start: Long, end: Long): Flow<List<CalendarEvent>> =
        eventDao.getInRange(accountId, start, end)

    override fun getEventsInRangeUnified(accountIds: List<String>, start: Long, end: Long): Flow<List<CalendarEvent>> =
        eventDao.getInRangeUnified(accountIds, start, end)

    override fun getEventsForDate(accountId: String, date: Long): Flow<List<CalendarEvent>> =
        eventDao.getForDate(accountId, date)

    override fun getEventsForDateUnified(accountIds: List<String>, date: Long): Flow<List<CalendarEvent>> =
        eventDao.getForDateUnified(accountIds, date)

    override fun getUpcomingEvents(accountId: String, now: Long, limit: Int): Flow<List<CalendarEvent>> =
        eventDao.getUpcoming(accountId, now, limit)

    override fun getUpcomingEventsUnified(accountIds: List<String>, now: Long, limit: Int): Flow<List<CalendarEvent>> =
        eventDao.getUpcomingUnified(accountIds, now, limit)

    override fun getRecurringEvents(accountId: String): Flow<List<CalendarEvent>> =
        eventDao.getRecurringEvents(accountId)

    override fun getOrganizedBy(accountId: String, email: String): Flow<List<CalendarEvent>> =
        eventDao.getOrganizedBy(accountId, email).map { it.filter { e -> e.organizer?.email == email } }

    override fun getAttendedBy(accountId: String, email: String): Flow<List<CalendarEvent>> =
        eventDao.getAttendedBy(accountId, email).map { it.filter { e -> e.attendees.any { a -> a.email == email } } }

    override fun searchEvents(query: String, accountIds: List<String>, limit: Int): Flow<List<CalendarEvent>> =
        eventDao.searchEvents("%$query%", accountIds, limit)

    override suspend fun getEventsNeedingSync(accountId: String): List<CalendarEvent> =
        eventDao.getNeedingSync(accountId)

    override suspend fun getLocalOnlyEvents(accountId: String): List<CalendarEvent> =
        eventDao.getLocalOnly(accountId)

    override suspend fun markEventCancelled(eventUid: String, accountId: String) =
        eventDao.markCancelled(eventUid, accountId)

    override suspend fun markEventSynced(id: String): Int = eventDao.markSynced(id)

    override suspend fun markAllEventsNeedingSync(accountId: String): Int =
        eventDao.markAllNeedingSync(accountId)

    override suspend fun cleanupCancelledEvents(accountId: String, olderThan: Long): Int =
        eventDao.cleanupCancelledEvents(accountId, olderThan)

    // Calendars
    override suspend fun insertCalendar(calendar: Calendar): Long = calDao.insert(calendar)

    override suspend fun insertCalendars(calendars: List<Calendar>): List<Long> = calDao.insertAll(calendars)

    override suspend fun updateCalendar(calendar: Calendar): Int = calDao.update(calendar)

    override suspend fun deleteCalendar(calendar: Calendar): Int = calDao.delete(calendar)

    override suspend fun getCalendarById(id: String): Calendar? = calDao.getById(id)

    override fun getCalendarsByAccount(accountId: String): Flow<List<Calendar>> = calDao.getByAccount(accountId)

    override fun getSelectedCalendarsByAccount(accountId: String): Flow<List<Calendar>> =
        calDao.getSelectedByAccount(accountId)

    override fun getSelectedCalendarsUnified(accountIds: List<String>): Flow<List<Calendar>> =
        calDao.getSelectedUnified(accountIds)

    override suspend fun getCalendarByServerId(accountId: String, serverId: String): Calendar? =
        calDao.getByServerId(accountId, serverId)

    override suspend fun setCalendarSelected(id: String, selected: Boolean): Int = calDao.setSelected(id, selected)

    override suspend fun updateLastSynced(accountId: String, now: Long): Int = calDao.updateLastSynced(accountId, now)
}