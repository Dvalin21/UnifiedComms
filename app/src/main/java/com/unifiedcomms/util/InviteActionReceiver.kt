package com.unifiedcomms.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.sync.CalendarSyncEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class InviteActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var calendarRepo: CalendarRepository
    
    @Inject
    lateinit var calendarSync: CalendarSyncEngine

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("event_id") ?: return
        val response = intent.getIntExtra("response", -1)
        val status = AttendeeStatus.values().getOrNull(response) ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            val event = calendarRepo.getEventById(eventId)
            event?.let { e ->
                val updatedAttendees = e.attendees.map { attendee ->
                    // Find current user and update their status
                    // For now, just update all (would need user ID matching)
                    if (attendee.status == AttendeeStatus.NEEDS_ACTION) {
                        attendee.copy(
                            status = status,
                            respondedAt = kotlinx.datetime.Instant.now()
                        )
                    } else {
                        attendee
                    }
                }
                
                val updatedEvent = e.copy(
                    attendees = updatedAttendees,
                    updatedAt = kotlinx.datetime.Instant.now(),
                    needsSync = true
                )
                
                calendarRepo.updateEvent(updatedEvent)
                calendarSync.updateEvent(e.accountId, updatedEvent)
            }
        }
    }
}