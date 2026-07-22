package com.unifiedcomms.sync

import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.DayOfWeek
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.model.RecurrenceDay
import com.unifiedcomms.data.model.RecurrenceFrequency
import com.unifiedcomms.data.model.RecurrenceRule
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.time.DayOfWeek as JDayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Expands a recurring master [CalendarEvent] into its individual occurrences that fall
 * within a requested window [windowStart, windowEnd] (epoch millis).
 *
 * Occurrences are generated on the fly at query time — we do NOT persist thousands of rows.
 * The master stays in the DB (recurrenceId == null, recurrenceRule != null); generated
 * instances carry recurrenceId = master.uid so they are flagged as derived occurrences.
 *
 * ponytail: wall-clock time is preserved across DST by doing period arithmetic on the event's
 * own timezone (ZonedDateTime), not by adding raw epoch deltas. Covers DAILY / WEEKLY (+BYDAY)
 * / MONTHLY (+BYMONTHDAY, +BYDAY) / YEARLY. SECONDLY/MINUTELY/HOURLY are ignored (return master
 * only) — they do not occur in real calendars and would spam the UI. COUNT / UNTIL honored.
 */
object RecurrenceExpander {
    // Bounds infinite recurrences (e.g. daily with no UNTIL) so a far-future query can't loop forever.
    private const val HARD_CAP = 3660L

    fun expand(master: CalendarEvent, windowStart: Long, windowEnd: Long): List<CalendarEvent> {
        val rule = master.recurrenceRule ?: return listOf(master)
        val freq = rule.freq
        if (freq !in listOf(
                RecurrenceFrequency.DAILY,
                RecurrenceFrequency.WEEKLY,
                RecurrenceFrequency.MONTHLY,
                RecurrenceFrequency.YEARLY
            )
        ) return listOf(master)

        val zone = runCatching { ZoneId.of(master.startAt.timeZone) }.getOrDefault(ZoneId.systemDefault())
        val startMs = master.startAt.toInstant().toEpochMilliseconds()
        val endMs = master.endAt.toInstant().toEpochMilliseconds()
        val durationMs = (endMs - startMs).coerceAtLeast(0L)
        val allDay = master.isAllDay()
        val base = java.time.Instant.ofEpochMilli(startMs).atZone(zone)
        val untilMs = rule.until?.toEpochMilliseconds()
        val horizon = minOf(windowEnd, untilMs ?: windowEnd)

        val seq = occurrenceSequence(base, rule)

        // Index exception overrides by their original occurrence instant (ms) for O(1) lookup.
        // EXDATE (deleted) and RECURRENCE-ID (moved/cancelled) instances both live here.
        val exceptionsByDate = master.recurrenceExceptions.associateBy { it.originalDate.toEpochMilliseconds() }

        val out = mutableListOf<CalendarEvent>()
        var index = 0L
        for (occ in seq) {
            if (index >= HARD_CAP) break
            val ms = occ.toInstant().toEpochMilli()
            if (untilMs != null && ms > untilMs) break
            if (ms > horizon) break
            val ex = exceptionsByDate[ms]
            if (ex != null) {
                // Any exception (EXDATE delete, or RECURRENCE-ID move/cancel) means this
                // generated occurrence is replaced/removed — never emit the original slot.
                // Moved overrides are re-emitted from their override event in the post-loop.
                index++
                if (rule.count != null && index >= rule.count) break
                continue
            }
            if (ms in windowStart..windowEnd) {
                out += if (ms == startMs) master
                else instanceFor(master, occ, durationMs, zone, allDay, index)
            }
            index++
            if (rule.count != null && index >= rule.count) break
        }

        // ponytail: emit non-deleted RECURRENCE-ID overrides as their own rows so a moved
        // instance shows its new time. Their original occurrence was suppressed above.
        for (ex in master.recurrenceExceptions) {
            val ev = ex.exceptionEvent ?: continue
            if (ex.isDeleted || ev.status == com.unifiedcomms.data.model.EventStatus.CANCELLED) continue
            val evMs = ev.startAt.toInstant().toEpochMilliseconds()
            if (evMs in windowStart..windowEnd) out.add(ev)
        }
        return out
    }

    private fun occurrenceSequence(base: ZonedDateTime, rule: RecurrenceRule): Sequence<ZonedDateTime> {
        val interval = rule.interval.coerceAtLeast(1)
        return when (rule.freq) {
            RecurrenceFrequency.DAILY -> sequence {
                var n = 0L
                while (true) {
                    yield(base.plus(Period.ofDays((n * interval).toInt())))
                    n++
                }
            }
            RecurrenceFrequency.WEEKLY -> {
                if (rule.byDay.isNotEmpty()) {
                    val days = rule.byDay.sortedBy { it.day.ordinal }
                    sequence {
                        var w = 0L
                        while (true) {
                            val weekAnchor = base.plus(Period.ofWeeks((w * interval).toInt()))
                            for (rd in days) {
                                yield(weekdayInWeek(weekAnchor, modelDowToJava(rd.day), base.toLocalTime()))
                            }
                            w++
                        }
                    }
                } else {
                    sequence {
                        var n = 0L
                        while (true) {
                            yield(base.plus(Period.ofWeeks((n * interval).toInt())))
                            n++
                        }
                    }
                }
            }
            RecurrenceFrequency.MONTHLY -> {
                when {
                    rule.byDay.isNotEmpty() -> sequence {
                        var m = 0L
                        while (true) {
                            val monthBase = base.plus(Period.ofMonths((m * interval).toInt()))
                            // ponytail: RFC 5545 BYDAY=MO,WE,FR (no ordinal) = ALL Mondays,
                            // Wednesdays, Fridays in the month. If a weekday carries an ordinal
                            // (e.g. BYDAY=1MO), emit only that nth occurrence.
                            for (day in rule.byDay) {
                                if (day.weekNumber == null) {
                                    for (occ in allWeekdaysInMonth(monthBase, day)) yield(occ)
                                } else {
                                    yield(monthWeekday(monthBase, day))
                                }
                            }
                            m++
                        }
                    }
                    rule.byMonthDay.isNotEmpty() -> sequence {
                        var m = 0L
                        while (true) {
                            val mb = base.plus(Period.ofMonths((m * interval).toInt()))
                            val daysInMonth = mb.toLocalDate().lengthOfMonth()
                            for (dayVal in rule.byMonthDay) {
                                val day = dayVal.coerceIn(1, daysInMonth)
                                yield(mb.withDayOfMonth(day))
                            }
                            m++
                        }
                    }
                    else -> sequence {
                        var m = 0L
                        while (true) {
                            yield(base.plus(Period.ofMonths((m * interval).toInt())))
                            m++
                        }
                    }
                }
            }
            RecurrenceFrequency.YEARLY -> sequence {
                var y = 0L
                while (true) {
                    yield(base.plus(Period.ofYears((y * interval).toInt())))
                    y++
                }
            }
            else -> sequenceOf(base)
        }
    }

    // First date in [anchor, anchor+6d] that falls on jdow, at the given wall-clock time.
    private fun weekdayInWeek(anchor: ZonedDateTime, jdow: JDayOfWeek, time: LocalTime): ZonedDateTime {
        var d = anchor.toLocalDate()
        repeat(7) {
            if (d.dayOfWeek == jdow) return ZonedDateTime.of(d, time, anchor.zone)
            d = d.plusDays(1)
        }
        return ZonedDateTime.of(d, time, anchor.zone)
    }

    // nth (or last, if weekNumber < 0) weekday of the month containing monthBase.
    private fun monthWeekday(monthBase: ZonedDateTime, recDay: RecurrenceDay): ZonedDateTime {
        val jdow = modelDowToJava(recDay.day)
        val ym = YearMonth.of(monthBase.year, monthBase.month)
        var first = ym.atDay(1)
        while (first.dayOfWeek != jdow) first = first.plusDays(1)
        val wn = recDay.weekNumber ?: 1
        val date = if (wn > 0) {
            val d = first.plusDays((wn - 1L) * 7)
            if (d.month != ym.month) ym.atEndOfMonth() else d
        } else {
            var d = first
            while (true) {
                val n = d.plusDays(7)
                if (n.month != ym.month) break
                d = n
            }
            if (wn < -1) d.minusDays(((-wn - 1L) * 7)) else d
        }
        return ZonedDateTime.of(date, monthBase.toLocalTime(), monthBase.zone)
    }

    // Every occurrence of the given weekday within the month containing monthBase.
    private fun allWeekdaysInMonth(monthBase: ZonedDateTime, recDay: RecurrenceDay): List<ZonedDateTime> {
        val jdow = modelDowToJava(recDay.day)
        val ym = YearMonth.of(monthBase.year, monthBase.month)
        var d = ym.atDay(1)
        while (d.dayOfWeek != jdow) d = d.plusDays(1)
        val out = mutableListOf<ZonedDateTime>()
        while (d.month == ym.month) {
            out.add(ZonedDateTime.of(d, monthBase.toLocalTime(), monthBase.zone))
            d = d.plusDays(7)
        }
        return out
    }

    private fun instanceFor(
        master: CalendarEvent,
        occStart: ZonedDateTime,
        durationMs: Long,
        zone: ZoneId,
        allDay: Boolean,
        index: Long
    ): CalendarEvent {
        val tz = TimeZone.of(zone.id)
        val startInstant = Instant.fromEpochMilliseconds(occStart.toInstant().toEpochMilli())
        val endInstant = Instant.fromEpochMilliseconds(occStart.toInstant().toEpochMilli() + durationMs)
        return master.copy(
            id = "${master.id}#$index",
            recurrenceId = master.uid,
            uid = master.uid,
            startAt = EventDateTime.fromInstant(startInstant, tz, allDay),
            endAt = EventDateTime.fromInstant(endInstant, tz, allDay),
            recurrenceRule = null,
            isLocalOnly = false,
            isCancelled = false
        )
    }

    private fun modelDowToJava(d: DayOfWeek): JDayOfWeek = when (d) {
        DayOfWeek.SU -> JDayOfWeek.SUNDAY
        DayOfWeek.MO -> JDayOfWeek.MONDAY
        DayOfWeek.TU -> JDayOfWeek.TUESDAY
        DayOfWeek.WE -> JDayOfWeek.WEDNESDAY
        DayOfWeek.TH -> JDayOfWeek.THURSDAY
        DayOfWeek.FR -> JDayOfWeek.FRIDAY
        DayOfWeek.SA -> JDayOfWeek.SATURDAY
    }
}
