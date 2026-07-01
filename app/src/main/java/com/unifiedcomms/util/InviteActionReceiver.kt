package com.unifiedcomms.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.db.dao.CalendarEventDao
import com.unifiedcomms.data.db.dao.CalendarDao
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.security.CryptoManager
import com.unifiedcomms.security.CryptoManagerImpl
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
        val calendarEventDao = db.calendarEventDao()
        val calendarDao = db.calendarDao()
        val calendarRepo = CalendarRepositoryImpl(calendarEventDao, calendarDao)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), CryptoManagerImpl(context))
        val crypto = CryptoManagerImpl(context)
        val calendarSync = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, CoroutineScope(Dispatchers.IO))

        CoroutineScope(Dispatchers.IO).launch {
            val event = calendarRepo.getEventById(eventId)
            event?.let { e ->
                val account = accountRepo.getById(e.accountId) ?: return@launch
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
                calendarSync.updateEvent(account, updatedEvent)
            }
        }
    }
}