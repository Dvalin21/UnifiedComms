package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.Calendar
import kotlinx.coroutines.flow.Flow

interface CalendarSyncEngine {
    suspend fun syncAccount(account: Account): SyncResult
    suspend fun syncCalendar(account: Account, calendar: Calendar): SyncResult
    suspend fun fetchEvent(account: Account, calendarId: String, uid: String): CalendarEvent?
    suspend fun createEvent(account: Account, event: CalendarEvent): CreateResult
    suspend fun updateEvent(account: Account, event: CalendarEvent): SyncResult
    suspend fun deleteEvent(account: Account, calendarId: String, uid: String): SyncResult
    suspend fun respondToInvite(account: Account, eventUid: String, status: com.unifiedcomms.data.model.AttendeeStatus, comment: String?): SyncResult
    fun observeSyncProgress(accountId: String): Flow<SyncProgress>
    suspend fun testConnection(account: Account): ConnectionTestResult
    suspend fun getCalendars(account: Account): List<Calendar>
}

data class CreateResult(
    val success: Boolean,
    val serverId: String? = null,
    val uid: String? = null,
    val etag: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success(serverId: String, uid: String, etag: String? = null): CreateResult = CreateResult(true, serverId, uid, etag)
        fun failure(error: String): CreateResult = CreateResult(false, errorMessage = error)
    }
}