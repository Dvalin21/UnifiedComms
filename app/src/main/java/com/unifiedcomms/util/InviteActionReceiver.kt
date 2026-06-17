package com.unifiedcomms.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.db.dao.CalendarEventDao
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.sync.CalendarSyncEngine
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InviteActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("event_id") ?: return
        val response = intent.getIntExtra("response", -1)
        val status = AttendeeStatus.values().getOrNull(response) ?: return

        // Manually get dependencies since Hilt is disabled
        val db = UnifiedCommsDatabase.getInstance(context)
        val calendarDao = db.calendarEventDao()
        val calendarRepo = CalendarRepositoryImpl(calendarDao)
        val calendarSync = CalendarSyncEngineImpl(calendarRepo, null, null)

        CoroutineScope(Dispatchers.IO).launch {
            val event = calendarRepo.getEventById(eventId)
            event?.let { e ->
                val updatedAttendees = e.attendees.map { attendee ->
                    // Find current user and update their status
                    // For now, just update all (would need user ID matching)
                    if (attendee.status == AttendeeStatus.NEEDS_ACTION) {
                        attendee.copy(
                            status = status,
                            respondedAt = kotlinx.datetime.Clock.System.now()
                        )
                    } else {
                        attendee
                    }
                }

                val updatedEvent = e.copy(
                    attendees = updatedAttendees,
                    updatedAt = kotlinx.datetime.Clock.System.now(),
                    needsSync = true
                )

                calendarRepo.updateEvent(updatedEvent)
                calendarSync.updateEvent(e.accountId, updatedEvent)
            }
        }
    }
}