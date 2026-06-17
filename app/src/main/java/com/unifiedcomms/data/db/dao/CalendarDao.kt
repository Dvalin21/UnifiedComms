package com.unifiedcomms.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.Calendar
import com.unifiedcomms.data.model.EventStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Dao
interface CalendarEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEvent>): List<Long>

    @Update
    suspend fun update(event: CalendarEvent): Int

    @Update
    suspend fun updateAll(events: List<CalendarEvent>): Int

    @Delete
    suspend fun delete(event: CalendarEvent): Int

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM calendar_events WHERE accountId = :accountId AND calendarId = :calendarId")
    suspend fun deleteByAccountAndCalendar(accountId: String, calendarId: String): Int

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getById(id: String): CalendarEvent?

    @Query("SELECT * FROM calendar_events WHERE uid = :uid AND accountId = :accountId")
    suspend fun getByUid(uid: String, accountId: String): CalendarEvent?

    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId AND calendarId = :calendarId ORDER BY startAt ASC")
    fun getByCalendar(accountId: String, calendarId: String): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId AND isCancelled = 0 ORDER BY startAt ASC")
    fun getAllForAccount(accountId: String): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE accountId IN (:accountIds) AND isCancelled = 0 ORDER BY startAt ASC")
    fun getUnifiedCalendar(accountIds: List<String>): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId AND startAt >= :start AND startAt <= :end AND isCancelled = 0 ORDER BY startAt ASC")
    fun getInRange(accountId: String, start: Long, end: Long): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE accountId IN (:accountIds) AND startAt >= :start AND startAt <= :end AND isCancelled = 0 ORDER BY startAt ASC")
    fun getInRangeUnified(accountIds: List<String>, start: Long, end: Long): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId AND date(startAt) = :date AND isCancelled = 0 ORDER BY startAt ASC")
    fun getForDate(accountId: String, date: Long): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE accountId IN (:accountIds) AND date(startAt) = :date AND isCancelled = 0 ORDER BY startAt ASC")
    fun getForDateUnified(accountIds: List<String>, date: Long): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId AND startAt >= :now AND isCancelled = 0 ORDER BY startAt ASC LIMIT :limit")
    fun getUpcoming(accountId: String, now: Long, limit: Int): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE accountId IN (:accountIds) AND startAt >= :now AND isCancelled = 0 ORDER BY startAt ASC LIMIT :limit")
    fun getUpcomingUnified(accountIds: List<String>, now: Long, limit: Int): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE recurrenceRule IS NOT NULL AND accountId = :accountId")
    fun getRecurringEvents(accountId: String): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE organizer.email = :email AND accountId = :accountId AND isCancelled = 0 ORDER BY startAt ASC")
    fun getOrganizedBy(accountId: String, email: String): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE :email IN (SELECT attendee.email FROM json_each(attendees)) AND accountId = :accountId AND isCancelled = 0 ORDER BY startAt ASC")
    fun getAttendedBy(accountId: String, email: String): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE (title LIKE :query OR description LIKE :query OR location LIKE :query) AND accountId IN (:accountIds) AND isCancelled = 0 ORDER BY startAt DESC LIMIT :limit")
    fun searchEvents(query: String, accountIds: List<String>, limit: Int): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE needsSync = 1 AND accountId = :accountId")
    suspend fun getNeedingSync(accountId: String): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE isLocalOnly = 1 AND accountId = :accountId")
    suspend fun getLocalOnly(accountId: String): List<CalendarEvent>

    @Transaction
    suspend fun markCancelled(eventUid: String, accountId: String) {
        getByUid(eventUid, accountId)?.let { event ->
            update(event.copy(isCancelled = true, status = EventStatus.CANCELLED, updatedAt = Instant.now(), needsSync = true))
        }
    }

    @Query("UPDATE calendar_events SET needsSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String): Int

    @Query("UPDATE calendar_events SET needsSync = 1 WHERE accountId = :accountId")
    suspend fun markAllNeedingSync(accountId: String): Int

    @Query("DELETE FROM calendar_events WHERE accountId = :accountId AND isCancelled = 1 AND updatedAt < :olderThan")
    suspend fun cleanupCancelledEvents(accountId: String, olderThan: Long): Int
}

@Dao
interface CalendarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calendar: Calendar): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calendars: List<Calendar>): List<Long>

    @Update
    suspend fun update(calendar: Calendar): Int

    @Delete
    suspend fun delete(calendar: Calendar): Int

    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun getById(id: String): Calendar?

    @Query("SELECT * FROM calendars WHERE accountId = :accountId ORDER BY isPrimary DESC, name ASC")
    fun getByAccount(accountId: String): Flow<List<Calendar>>

    @Query("SELECT * FROM calendars WHERE accountId = :accountId AND isSelected = 1 ORDER BY isPrimary DESC, name ASC")
    fun getSelectedByAccount(accountId: String): Flow<List<Calendar>>

    @Query("SELECT * FROM calendars WHERE accountId IN (:accountIds) AND isSelected = 1 ORDER BY isPrimary DESC, name ASC")
    fun getSelectedUnified(accountIds: List<String>): Flow<List<Calendar>>

    @Query("SELECT * FROM calendars WHERE accountId = :accountId AND serverId = :serverId")
    suspend fun getByServerId(accountId: String, serverId: String): Calendar?

    @Query("UPDATE calendars SET isSelected = :selected WHERE id = :id")
    suspend fun setSelected(id: String, selected: Boolean): Int

    @Query("UPDATE calendars SET lastSyncedAt = :now WHERE accountId = :accountId")
    suspend fun updateLastSynced(accountId: String, now: Long): Int
}