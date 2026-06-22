package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.CalendarEventDao
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventAttendee
import com.unifiedcomms.data.model.EventDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CalendarRepositoryImplTest {

    private lateinit var eventDao: CalendarEventDao
    private lateinit var repo: CalendarRepositoryImpl

    @Before
    fun setUp() {
        eventDao = mock()
        repo = CalendarRepositoryImpl(eventDao, mock())
    }

    @Test
    fun `getOrganizedBy filters by organizer email`() = runTest {
        val event = CalendarEvent(
            id = "1",
            accountId = "a",
            calendarId = "c",
            uid = "u1",
            title = "Meeting",
            startAt = EventDateTime(),
            endAt = EventDateTime(),
            organizer = EventAttendee(email = "org@example.com", name = "Organizer")
        )
        whenever(eventDao.getOrganizedBy(any(), any())).thenReturn(flowOf(listOf(event)))
        val result = repo.getOrganizedBy("a", "org@example.com").first()
        assertEquals(listOf(event), result)
    }

    @Test
    fun `getAttendedBy filters by attendee email`() = runTest {
        val event = CalendarEvent(
            id = "1",
            accountId = "a",
            calendarId = "c",
            uid = "u1",
            title = "Meeting",
            startAt = EventDateTime(),
            endAt = EventDateTime(),
            attendees = listOf(EventAttendee(email = "att@example.com"))
        )
        whenever(eventDao.getAttendedBy(any(), any())).thenReturn(flowOf(listOf(event)))
        val result = repo.getAttendedBy("a", "att@example.com").first()
        assertEquals(listOf(event), result)
    }

    @Test
    fun `insert delegates to dao`() = runTest {
        val event = CalendarEvent(
            id = "1",
            accountId = "a",
            calendarId = "c",
            uid = "u1",
            title = "Event",
            startAt = EventDateTime(),
            endAt = EventDateTime()
        )
        repo.insertEvent(event)
        verify(eventDao).insert(event)
    }

    @Test
    fun `delete delegates to dao`() = runTest {
        repo.deleteEventById("1")
        verify(eventDao).deleteById("1")
    }
}
