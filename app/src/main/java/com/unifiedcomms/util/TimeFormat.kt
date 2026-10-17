package com.unifiedcomms.util

import android.content.Context
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * ponytail: 12- vs 24-hour display is a system setting, not a hardcode. These
 * helpers honour it. The call sites previously baked in "h:mm a" (and one baked
 * in "HH:mm"), so a 24-hour-locale user saw 15:30 rendered as "3:30 PM" — and
 * CalendarScreen showed both formats on the same screen.
 */
object TimeFormat {

    private fun use24Hour(context: Context): Boolean =
        android.text.format.DateFormat.is24HourFormat(context)

    private fun timePattern(context: Context): String =
        if (use24Hour(context)) "HH:mm" else "h:mm a"

    /** "3:30 PM" / "15:30" */
    fun time(temporal: TemporalAccessor, context: Context): String =
        DateTimeFormatter.ofPattern(timePattern(context), Locale.getDefault()).format(temporal)

    /** "Sep 26, 3:30 PM" / "Sep 26, 15:30" */
    fun shortDateTime(temporal: TemporalAccessor, context: Context): String =
        DateTimeFormatter.ofPattern(
            if (use24Hour(context)) "MMM d, HH:mm" else "MMM d, h:mm a",
            Locale.getDefault()
        ).format(temporal)

    /** "Sep 26, 2026 3:30 PM" / "Sep 26, 2026 15:30" */
    fun dateTime(temporal: TemporalAccessor, context: Context): String =
        DateTimeFormatter.ofPattern(
            if (use24Hour(context)) "MMM d, yyyy HH:mm" else "MMM d, yyyy h:mm a",
            Locale.getDefault()
        ).format(temporal)

    /** "Fri, 26 Sep 2026 3:30 PM" / "Fri, 26 Sep 2026 15:30" */
    fun fullDateTime(temporal: TemporalAccessor, context: Context): String =
        DateTimeFormatter.ofPattern(
            if (use24Hour(context)) "EEE, d MMM yyyy HH:mm" else "EEE, d MMM yyyy h:mm a",
            Locale.getDefault()
        ).format(temporal)
}
