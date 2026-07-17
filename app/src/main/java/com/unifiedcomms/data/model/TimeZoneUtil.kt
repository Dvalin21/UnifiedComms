package com.unifiedcomms.data.model

import java.time.ZoneId
import kotlinx.datetime.TimeZone

/**
 * iCalendar TZIDs are case-insensitive per RFC 5545, but both java.time.ZoneId and
 * kotlinx.datetime.TimeZone parse them case-sensitively. Servers routinely emit
 * non-canonical case (e.g. "AMERICA/NEW_YORK"), which would otherwise throw and drop the
 * whole VEVENT/VTODO. These helpers resolve such ids case-insensitively against the
 * platform zone table and NEVER throw — they fall back to the canonical form or UTC.
 */
object TimeZoneUtil {
    private val cache = mutableMapOf<String, String?>()

    /** Normalize an iCal TZID to its canonical IANA string (e.g. "AMERICA/NEW_YORK" -> "America/New_York"). */
    fun normalize(tzId: String?): String? {
        if (tzId.isNullOrBlank()) return null
        cache[tzId]?.let { return it }
        val direct = runCatching { ZoneId.of(tzId) }.getOrNull()
        if (direct != null) { cache[tzId] = direct.id; return direct.id }
        val lower = tzId.lowercase()
        val matched = ZoneId.getAvailableZoneIds().firstOrNull { it.lowercase() == lower }
        val resolved = matched ?: runCatching { ZoneId.of(tzId) }.getOrNull()?.id
        val result = resolved ?: "UTC"
        cache[tzId] = result
        return result
    }

    /** Resolve an iCal TZID to a kotlinx.datetime.TimeZone, falling back to UTC on miss. */
    fun toKtxZone(tzId: String?): TimeZone {
        val normalized = normalize(tzId) ?: return TimeZone.UTC
        return runCatching { TimeZone.of(normalized) }.getOrNull() ?: TimeZone.UTC
    }
}
