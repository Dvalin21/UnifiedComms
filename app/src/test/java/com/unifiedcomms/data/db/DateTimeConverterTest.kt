package com.unifiedcomms.data.db

import com.unifiedcomms.data.db.converters.DateTimeConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class DateTimeConverterTest {

    private val converter = DateTimeConverter()

    @Test
    fun `instant round trip`() {
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val millis = converter.fromInstant(now)
        val back = converter.toInstant(millis)
        assertEquals(now, back)
    }

    @Test
    fun `null instant is preserved`() {
        val millis = converter.fromInstant(null)
        assertEquals(null, millis)
        val back = converter.toInstant(null)
        assertEquals(null, back)
    }

    @Test
    fun `local date round trip`() {
        val date = LocalDate(2026, 6, 22)
        val str = converter.fromLocalDate(date)
        val back = converter.toLocalDate(str)
        assertEquals(date, back)
    }
}
