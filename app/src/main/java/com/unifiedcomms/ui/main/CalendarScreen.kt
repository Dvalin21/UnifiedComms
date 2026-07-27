package com.unifiedcomms.ui.main
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.GenericShape
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import com.unifiedcomms.data.model.CalendarEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: MainViewModel,
    onCreateEvent: () -> Unit,
    onEventClick: (String) -> Unit
) {
    var selectedView by remember { mutableStateOf(CalendarView.MONTH) }
    val currentDate = remember { mutableStateOf(java.time.LocalDate.now()) }
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccountIds = accounts.filter { it.isActive }.map { it.id }
    // ponytail: Room's `accountId IN ()` with an empty list throws; guard it so the
    // Flow never errors on first composition (before accounts emit) and stays empty.
    // Also expand recurring events into the visible window (getUnifiedEvents returned
    // raw masters only, so repeats never appeared).
    // FIX (2026-07-23): the window was a narrow ~1-5 week slice around the current
    // month, so any event outside it (next month's appointment, a birthday, a
    // recurring series) silently never rendered -> looked like "calendar not
    // syncing". The engine DID sync it into Room; the query just excluded it. Use a
    // full-year span so every synced event is always in the loaded set; the
    // day/week/month views slice this same data by their own cell dates, so nothing
    // is lost and far-future/past events now appear. Window recomputed on date change.
    val eventWindow = remember(currentDate.value) {
        val now = currentDate.value
        val z = java.time.ZoneId.systemDefault()
        val start = now.withDayOfYear(1).atStartOfDay(z).toInstant().toEpochMilli()
        val end = now.withDayOfYear(1).plusYears(1).atStartOfDay(z).toInstant().toEpochMilli()
        start to end
    }
    val baseFlow: kotlinx.coroutines.flow.Flow<List<com.unifiedcomms.data.model.CalendarEvent>> =
        if (activeAccountIds.isEmpty()) kotlinx.coroutines.flow.flowOf<List<com.unifiedcomms.data.model.CalendarEvent>>(emptyList())
        else viewModel.calendarRepository.getEventsInRangeUnified(activeAccountIds, eventWindow.first, eventWindow.second)
    val rawEvents by baseFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // ponytail: hide legacy imported events (Google/creator UIDs ending in
    // @google.com). The CalDAV collection still serves them (confirmed: 1224
    // of 1587 server hrefs are @google.com), but they are not part of the
    // user's current calendar and must not render. Display-only filter — we
    // do NOT delete them server-side (don't mess up the calendar).
    val allEvents = remember(rawEvents) { rawEvents.filter { !isLegacyImported(it) && !isCancelled(it) } }

    // ponytail: Room already holds every synced event persistently — the user
    // wants calendar STORAGE, not re-streaming on every tab open. The previous
    // LaunchedEffect keyed on a fresh List each recomposition and gated on
    // allEvents.isEmpty(), but collectAsStateWithLifecycle's INITIAL value is
    // emptyList(), so it fired a full delete-then-insert re-sync on every open
    // (~35s of blue flash while the old rows were deleted and re-inserted).
    // Fix: read the DB DIRECTLY (suspend) to decide if a sync is truly needed.
    // If Room already has events for these accounts, never re-stream — just
    // display what's stored. Background WorkManager keeps it fresh.
    val accountKey = remember(activeAccountIds) { activeAccountIds.sorted().joinToString(",") }
    LaunchedEffect(accountKey) {
        if (accountKey.isNotEmpty()) {
            val firstId = activeAccountIds.firstOrNull()
            val stored: List<CalendarEvent> = if (firstId != null) {
                runCatching { viewModel.calendarRepository.getAllEventsForAccount(firstId).first() }.getOrElse { emptyList() }
            } else emptyList()
            if (stored.isEmpty()) {
                viewModel.syncCalendarForAccounts(activeAccountIds)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentDate.value.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                actions = {
                    // ponytail: grouped, evenly-sized controls. Prev/Next step the month;
                    // Today jumps back. View switch is a 3-segment pill so the active
                    // mode is obvious. All icons are fixed 24dp and never clip (no Text in
                    // an IconButton slot). This matches the clean market-calendar header
                    // (rounded, uncluttered) without naming any vendor.
                    IconButton(onClick = { currentDate.value = currentDate.value.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                    }
                    IconButton(onClick = { currentDate.value = java.time.LocalDate.now() }) {
                        Icon(Icons.Default.Today, contentDescription = "Today")
                    }
                    IconButton(onClick = { currentDate.value = currentDate.value.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                    }
                    Spacer(Modifier.width(4.dp))
                    // 3-segment view switch pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 1.dp
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            CalendarView.values().forEach { v ->
                                val selected = selectedView == v
                                val label = when (v) {
                                    CalendarView.DAY -> "Day"
                                    CalendarView.WEEK -> "Week"
                                    CalendarView.MONTH -> "Month"
                                }
                                Box(
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clickable { selectedView = v }
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (v != CalendarView.MONTH) Spacer(Modifier.width(2.dp))
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = { }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(bottom = 88.dp)) {
                when (selectedView) {
                    CalendarView.DAY -> DayView(date = currentDate.value, events = allEvents, onEventClick = onEventClick, onDateSelected = { currentDate.value = it })
                    CalendarView.WEEK -> WeekView(date = currentDate.value, events = allEvents, onEventClick = onEventClick, onDateSelected = { currentDate.value = it; selectedView = CalendarView.DAY })
                    CalendarView.MONTH -> MonthView(date = currentDate.value, allEvents = allEvents, onDayClick = { date -> currentDate.value = date; selectedView = CalendarView.DAY })
                }
            }
            // Samsung-style quick-add: a single FAB (the persistent text pill was
            // redundant clutter — both opened the same new-event flow).
            androidx.compose.material3.FloatingActionButton(
                onClick = onCreateEvent,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create event")
            }
        }
    }
}

enum class CalendarView { DAY, WEEK, MONTH }

// ponytail: right-pointing triangle used as the current-time pointer (Samsung-style),
// drawn on the left edge of the red "now" line in Day view.
val TriangleEdgeShape = GenericShape { size, _ ->
    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width, size.height / 2f)
        lineTo(0f, size.height)
        close()
    }
    path
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayView(date: java.time.LocalDate, events: List<CalendarEvent>, onEventClick: (String) -> Unit, onDateSelected: (java.time.LocalDate) -> Unit) {
    val HOUR_H = 56.dp
    val dayEvents = events.filter { !isLegacyImported(it) && !isCancelled(it) && isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), date) }
    val allDay = dayEvents.filter { it.isAllDay() }
    val timed = dayEvents.filter { !it.isAllDay() }
    val now = java.time.LocalDateTime.now()
    val isToday = date == java.time.LocalDate.now()
    val vscroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(isToday) {
        if (isToday) {
            val yDp = (now.hour * 60 + now.minute) / 60f * HOUR_H.value
            vscroll.scrollTo(((yDp - 120f) * density.density).toInt().coerceAtLeast(0))
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${date.dayOfWeek}, ${date.month} ${date.dayOfMonth}",
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        if (allDay.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allDay.forEach { ev ->
                    EventChip(event = ev.toMockEvent(), onClick = { onEventClick(ev.id) },
                        modifier = Modifier.width(150.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
        Box(modifier = Modifier.fillMaxSize().verticalScroll(vscroll)) {
            // hour grid background (time labels + lines)
            Column {
                for (h in 0..23) {
                    Row(modifier = Modifier.fillMaxWidth().height(HOUR_H)) {
                        Text(
                            text = java.time.LocalTime.of(h, 0).format(java.time.format.DateTimeFormatter.ofPattern("h a")),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(56.dp).padding(top = 2.dp), textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                            modifier = Modifier.weight(1f).align(Alignment.Top)
                        )
                    }
                }
            }
            // events positioned by absolute start/duration (Etar model)
            timed.forEach { ev ->
                val sMin = eventStartMinutes(ev)
                val eMin = eventEndMinutes(ev, sMin)
                val top = (sMin / 60f) * HOUR_H.value
                val hgt = ((eMin - sMin).coerceAtLeast(30) / 60f) * HOUR_H.value
                Box(
                    modifier = Modifier.offset(y = top.dp).padding(start = 66.dp, end = 16.dp)
                        .fillMaxWidth().height(hgt.dp)
                ) {
                    EventChip(
                        event = ev.toMockEvent(),
                        onClick = { onEventClick(ev.id) },
                        modifier = Modifier.fillMaxWidth().height(hgt.dp)
                    )
                }
            }
            // current-time line
            if (isToday) {
                val nowMin = now.hour * 60 + now.minute
                val lineY = (nowMin / 60f) * HOUR_H.value
                Box(
                    modifier = Modifier.offset(y = lineY.dp).padding(start = 64.dp)
                        .fillMaxWidth().height(2.dp).background(Color(0xFFE53935))
                )
                Box(
                    modifier = Modifier.offset(y = (lineY - 5f).dp).size(12.dp)
                        .background(Color(0xFFE53935), TriangleEdgeShape)
                )
            }
        }
    }
}

// ponytail: Samsung-style 7-day week strip (Sun-start). Selected day gets a white
// outline circle; days with events show a colored dot. Tapping a day switches the view.
@Composable
private fun WeekStripRow(date: java.time.LocalDate, events: List<CalendarEvent>, onDateSelected: (java.time.LocalDate) -> Unit) {
    val weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val dayInitials = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEachIndexed { i, day ->
            val selected = day == date
            val hasEvents = events.any { !isLegacyImported(it) && !isCancelled(it) && isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), day) }
            val dotColor = events.firstOrNull { !isLegacyImported(it) && !isCancelled(it) && isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), day) }
                ?.let { runCatching { Color(android.graphics.Color.parseColor(com.unifiedcomms.ui.theme.ColorNormalizer.normalize(it.color.background))) }.getOrNull() }
                ?: MaterialTheme.colorScheme.primary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDateSelected(day) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dayInitials[i],
                    fontSize = 11.sp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .then(if (selected) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (hasEvents) {
                    Box(modifier = Modifier.size(5.dp).background(dotColor, CircleShape))
                } else {
                    Spacer(modifier = Modifier.size(5.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekView(date: java.time.LocalDate, events: List<CalendarEvent>, onEventClick: (String) -> Unit, onDateSelected: (java.time.LocalDate) -> Unit) {
    val HOUR_H = 56.dp
    val weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val now = java.time.LocalDateTime.now()
    val isToday = date == java.time.LocalDate.now()
    val vscroll = rememberScrollState()
    val hscroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(isToday) {
        if (isToday) {
            val yDp = (now.hour * 60 + now.minute) / 60f * HOUR_H.value
            vscroll.scrollTo(((yDp - 120f) * density.density).toInt().coerceAtLeast(0))
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        WeekStripRow(date = date, events = events, onDateSelected = onDateSelected)
        // all-day strip across the week
        val allDayByDay = days.map { d -> events.filter { !isLegacyImported(it) && !isCancelled(it) && it.isAllDay() && isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), d) } }
        if (allDayByDay.any { it.isNotEmpty() }) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 64.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                days.forEachIndexed { i, d ->
                    Column(modifier = Modifier.width(110.dp)) {
                        allDayByDay[i].take(2).forEach { ev ->
                            EventChip(event = ev.toMockEvent(), onClick = { onEventClick(ev.id) }, modifier = Modifier.fillMaxWidth())
                        }
                        if (allDayByDay[i].size > 2) Text("+${allDayByDay[i].size - 2}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
        // shared-scroll time grid: time column (fixed left) + 7 day columns (horizontal scroll)
        Row(modifier = Modifier.fillMaxSize()) {
            // time labels — vertical scroll SHARED with the day grid (Etar model)
            Column(modifier = Modifier.width(56.dp).verticalScroll(vscroll)) {
                Spacer(modifier = Modifier.height(8.dp))
                for (h in 0..23) {
                    Box(modifier = Modifier.height(HOUR_H), contentAlignment = Alignment.TopEnd) {
                        Text(
                            text = java.time.LocalTime.of(h, 0).format(java.time.format.DateTimeFormatter.ofPattern("h a")),
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // day grid — same vscroll, horizontal scroll for 7 columns
            Row(modifier = Modifier.fillMaxSize().horizontalScroll(hscroll).verticalScroll(vscroll)) {
                days.forEach { d ->
                    val selected = d == date
                    val dayEvents = events.filter { !isLegacyImported(it) && !isCancelled(it) && !it.isAllDay() && isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), d) }
                    // Box (not Column) so the grid + events overlay at absolute time positions
                    Box(
                        modifier = Modifier.width(110.dp)
                            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), RoundedCornerShape(12.dp)) else Modifier)
                            .padding(4.dp)
                    ) {
                        // hour grid lines
                        Column {
                            for (h in 0..23) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                                    modifier = Modifier.height(HOUR_H)
                                )
                            }
                        }
                        // events positioned absolutely by start/duration
                        dayEvents.forEach { ev ->
                            val sMin = eventStartMinutes(ev)
                            val eMin = eventEndMinutes(ev, sMin)
                            val top = (sMin / 60f) * HOUR_H.value
                            val hgt = ((eMin - sMin).coerceAtLeast(30) / 60f) * HOUR_H.value
                            Box(
                                modifier = Modifier.offset(y = top.dp).padding(horizontal = 2.dp)
                                    .fillMaxWidth().height(hgt.dp)
                            ) {
                                EventChip(
                                    event = ev.toMockEvent(),
                                    onClick = { onEventClick(ev.id) },
                                    modifier = Modifier.fillMaxWidth().height(hgt.dp)
                                )
                            }
                        }
                        if (isToday && d == date) {
                            val nowMin = now.hour * 60 + now.minute
                            val lineY = (nowMin / 60f) * HOUR_H.value
                            Box(
                                modifier = Modifier.offset(y = lineY.dp).fillMaxWidth().height(2.dp)
                                    .background(Color(0xFFE53935))
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthView(date: java.time.LocalDate, allEvents: List<CalendarEvent>, onDayClick: (java.time.LocalDate) -> Unit) {
    val firstOfMonth = date.withDayOfMonth(1)
    val dayOfWeekOffset = firstOfMonth.dayOfWeek.value - 1 // Monday = 0
    val daysInMonth = firstOfMonth.lengthOfMonth()
    val today = java.time.LocalDate.now()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Weekday header strip (Mon..Sun), evenly weighted, no wrap/clip.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                Text(
                    text = d,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 6-week grid, 7 columns. Each cell is a fixed-height rounded surface.
        val cells = mutableListOf<java.time.LocalDate?>().apply {
            repeat(dayOfWeekOffset) { add(null) }
            for (d in 1..daysInMonth) add(firstOfMonth.plusDays((d - 1).toLong()))
            while (size % 7 != 0) add(null)
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    week.forEach { cellDate ->
                        val events = cellDate?.let { cd ->
                            allEvents.filter { ev ->
                                val evZone = java.time.ZoneId.of(
                                    com.unifiedcomms.data.model.TimeZoneUtil.normalize(ev.startAt.timeZone) ?: "UTC"
                                )
                                isSameDay(
                                    ev.startAt.toInstant(com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(ev.startAt.timeZone)),
                                    cd,
                                    evZone
                                ) && !isLegacyImported(ev) && !isCancelled(ev)
                            }
                        } ?: emptyList()
                        val isToday = cellDate == today
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .then(if (cellDate != null) Modifier.clickable { onDayClick(cellDate) } else Modifier)
                                .background(
                                    Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(6.dp)
                        ) {
                            if (cellDate != null) {
                                // ponytail: this Box was stacking the day number and the
                                // events Column as OVERLAPPING children (Box default), so the
                                // event bars painted on top of the date number. Wrap them in a
                                // Column so they flow vertically: number, then events below.
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Day number: today gets a SOLID filled circle badge
                                    // (Samsung One UI style).
                                    if (isToday) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .background(Color.White, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cellDate.dayOfMonth.toString(),
                                                color = Color.Black,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = cellDate.dayOfMonth.toString(),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (events.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val shown = events.take(3)
                                            shown.forEach { ev ->
                                                val barColor = Color(ev.color.toColorInt())
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(18.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(barColor)
                                                        .padding(horizontal = 6.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Text(
                                                        text = ev.title ?: "",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        textAlign = TextAlign.Start,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (events.size > shown.size) {
                                                Text(
                                                    text = "+${events.size - shown.size} more",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(start = 4.dp, top = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun rememberCurrentDateTime(): java.time.LocalDateTime {
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = java.time.LocalDateTime.now()
        }
    }
    return now
}

@Composable
fun CurrentTimePanel(events: List<CalendarEvent>) {
    val now = rememberCurrentDateTime()
    val today = java.time.LocalDate.now()
    val todayCount = events.count { !isLegacyImported(it) && !isCancelled(it) && isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), today) }
    val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d yyyy")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (todayCount == 0) "No events today" else "$todayCount event${if (todayCount == 1) "" else "s"} today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = now.toLocalDate().format(dateFmt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = now.toLocalTime().format(timeFmt),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

data class MockEvent(
    val id: String,
    val title: String,
    val startHour: Int,
    val endHour: Int,
    val color: Long,
    val calendarName: String,
    val isAllDay: Boolean = false
)

fun getMockEventsForDate(date: java.time.LocalDate): List<MockEvent> {
    val hash = abs(date.toString().hashCode())
    val count = (hash % 4) + 1
    val colors = listOf(0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFFBA68C8)
    return (0 until count).map { i ->
        MockEvent(
            id = "${date}-$i",
            title = "Event ${i + 1} for ${date.month}",
            startHour = 9 + (hash + i) % 10,
            endHour = 10 + (hash + i) % 10,
            color = colors[(hash + i) % colors.size],
            calendarName = "Calendar ${(hash + i) % 3 + 1}"
        )
    }
}

// ponytail: was zoning the instant to systemDefault, so an event in another tz could
// land on the wrong day cell. Pass the event's own zone so the day match is correct.
// ponytail: default zone keeps the 2-arg callers (most of them) simple; the 3-arg
// caller at MonthView passes the event's own zone for correctness.
private fun isSameDay(instant: kotlinx.datetime.Instant, date: java.time.LocalDate, zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean {
    return java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(zoneId)
        .toLocalDate() == date
}

// ponytail: legacy imported events carry UIDs like "...@google.com" (Google
// Calendar exports / the original creator's shared calendar). They are not part
// of the user's current calendar and must be hidden from every view. Display
// filter only — never delete server-side.
private fun isLegacyImported(event: com.unifiedcomms.data.model.CalendarEvent): Boolean {
    return event.uid.endsWith("@google.com", ignoreCase = true)
}

// ponytail: cancelled events (STATUS:CANCELLED) must not render. Display filter
// only — never delete server-side. Discontinued/old duplicates are a separate
// server-hygiene problem.
private fun isCancelled(event: com.unifiedcomms.data.model.CalendarEvent): Boolean {
    return event.status == com.unifiedcomms.data.model.EventStatus.CANCELLED
}

// 12-hour clock (e.g. 6 PM, not 18:00) for event chips and labels.
private fun format12h(hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 $ampm"
}

// ponytail: minutes from local midnight for an event's start, used to position
// timed events absolutely in the Day/Week grid (Etar model: one grid, events
// placed by duration, not bucketed per hour).
private fun eventStartMinutes(event: com.unifiedcomms.data.model.CalendarEvent): Int {
    val z = com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(event.startAt.timeZone)
    val ldt = event.startAt.toInstant(z).toLocalDateTime(z)
    return ldt.hour * 60 + ldt.minute
}

private fun eventEndMinutes(event: com.unifiedcomms.data.model.CalendarEvent, startMin: Int): Int {
    val z = com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(event.endAt.timeZone)
    val ldt = event.endAt.toInstant(z).toLocalDateTime(z)
    val raw = ldt.hour * 60 + ldt.minute
    // events that cross midnight (end < start) clamp to 24:00
    return if (raw <= startMin) 24 * 60 else raw
}

// ponytail: server TZIDs can be malformed (#2). Guard ZoneId.of with runCatching
// so a bad timezone doesn't crash the events list or detail screen.
private fun safeZoneId(tzId: String): java.time.ZoneId =
    runCatching { java.time.ZoneId.of(tzId) }.getOrNull() ?: java.time.ZoneId.systemDefault()

private fun com.unifiedcomms.data.model.CalendarEvent.toMockEvent(): MockEvent = MockEvent(
    id = id,
    title = title,
    startHour = java.time.Instant.ofEpochMilli(startAt.toInstant().toEpochMilliseconds())
        .atZone(safeZoneId(startAt.timeZone)).hour,
    endHour = java.time.Instant.ofEpochMilli(endAt.toInstant().toEpochMilliseconds())
        .atZone(safeZoneId(endAt.timeZone)).hour,
    color = color.toColorInt().toLong(),
    calendarName = title,
    isAllDay = startAt.isAllDay
)

@Composable
fun EventChip(event: MockEvent, compact: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.heightIn(min = 18.dp) else Modifier),
        shape = RoundedCornerShape(6.dp),
        color = Color(event.color),
        contentColor = Color.White,
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = if (compact) 3.dp else 6.dp)) {
            if (!compact) {
                Text(
                    text = "${format12h(event.startHour)} – ${format12h(event.endHour)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }
            Text(
                text = event.title,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    viewModel: MainViewModel,
    accountId: String,
    eventId: String? = null,
    onSave: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    var isAllDay by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(0xFFE57373) }
    var showDatePicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // ponytail: edit_event/{eventId} passes an existing event; load it so we UPDATE
    // instead of always INSERTing a new (duplicate) event (#17).
    var existingEvent by remember { mutableStateOf<com.unifiedcomms.data.model.CalendarEvent?>(null) }

    LaunchedEffect(eventId) {
        if (eventId != null) {
            val ev = viewModel.calendarRepository.getEventById(eventId)
            existingEvent = ev
            if (ev != null) {
                title = ev.title
                description = ev.description ?: ""
                location = ev.location ?: ""
                isAllDay = ev.startAt.isAllDay
                selectedColor = runCatching { android.graphics.Color.parseColor(com.unifiedcomms.ui.theme.ColorNormalizer.normalize(ev.color.background)) }.getOrNull()?.toLong() ?: selectedColor
                selectedDate = ev.startAt.date?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) }
                    ?: ev.startAt.dateTime?.date?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) }
                    ?: selectedDate
            }
        }
    }

    val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.toEpochDay() * 86400000)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Event") },
                navigationIcon = { IconButton(onClick = onSave) { Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Cancel") } },
                actions = {
                    IconButton(onClick = {
                        if (title.isNotBlank()) {
                            coroutineScope.launch {
                                val base = existingEvent
                                val (sh, sm) = if (base?.startAt?.dateTime != null) base.startAt.dateTime!!.hour to base.startAt.dateTime!!.minute else 9 to 0
                                val (eh, em) = if (base?.endAt?.dateTime != null) base.endAt.dateTime!!.hour to base.endAt.dateTime!!.minute else 10 to 0
                                val event = com.unifiedcomms.data.model.CalendarEvent(
                                    accountId = base?.accountId ?: accountId,
                                    calendarId = base?.calendarId ?: accountId,
                                    uid = base?.uid ?: java.util.UUID.randomUUID().toString(),
                                    title = title,
                                    description = description.takeIf { it.isNotBlank() },
                                    location = location.takeIf { it.isNotBlank() },
                                    startAt = com.unifiedcomms.data.model.EventDateTime(
                                        dateTime = kotlinx.datetime.LocalDateTime(
                                            selectedDate.year,
                                            selectedDate.monthValue,
                                            selectedDate.dayOfMonth,
                                            if (isAllDay) 0 else sh,
                                            if (isAllDay) 0 else sm
                                        ),
                                        date = kotlinx.datetime.LocalDate(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                                        timeZone = kotlinx.datetime.TimeZone.currentSystemDefault().id,
                                        isAllDay = isAllDay
                                    ),
                                    endAt = com.unifiedcomms.data.model.EventDateTime(
                                        dateTime = kotlinx.datetime.LocalDateTime(
                                            selectedDate.year,
                                            selectedDate.monthValue,
                                            selectedDate.dayOfMonth,
                                            if (isAllDay) 0 else eh,
                                            if (isAllDay) 0 else em
                                        ),
                                        date = kotlinx.datetime.LocalDate(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                                        timeZone = kotlinx.datetime.TimeZone.currentSystemDefault().id,
                                        isAllDay = isAllDay
                                    ),
                                    color = com.unifiedcomms.data.model.EventColor.fromInt(selectedColor.toInt()),
                                    isLocalOnly = base?.isLocalOnly ?: true
                                )
                                if (base != null) viewModel.calendarRepository.updateEvent(event) else viewModel.calendarRepository.insertEvent(event)
                                onSave()
                            }
                        }
                    }) { Icon(Icons.Default.Save, contentDescription = "Save") }
                }
            )
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            TextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            if (showDatePicker) {
                DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
                    TextButton(onClick = {
                        dateState.selectedDateMillis?.let { selectedDate = java.time.LocalDate.ofEpochDay(it / 86400000) }
                        showDatePicker = false
                    }) { Text("OK") }
                }, dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }) {
                    DatePicker(state = dateState)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it })
                Text("All day")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Date", fontWeight = FontWeight.Bold)
                TextButton(onClick = { showDatePicker = true }) { Text(selectedDate.toString()) }
            }

            Text(text = "Calendar Color", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFFBA68C8, 0xFF4FC3F7, 0xFF4DB6AC, 0xFFAED581).forEach { color ->
                    Surface(
                        modifier = Modifier.size(32.dp).background(
                            if (selectedColor == color) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            RoundedCornerShape(16.dp)
                        ).padding(4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(color),
                        onClick = { selectedColor = color }
                    ) { Spacer(modifier = Modifier.fillMaxSize()) }
                }
            }

            TextField(value = "", onValueChange = {}, label = { Text("Attendees (comma-separated emails)") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun EventDetailScreen(
    event: CalendarEvent,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    val eventTz = kotlinx.datetime.TimeZone.of(event.startAt.timeZone)
    val startZoned = java.time.Instant.ofEpochMilli(event.startAt.toInstant(eventTz).toEpochMilliseconds())
        .atZone(safeZoneId(event.startAt.timeZone))
    val endZoned = java.time.Instant.ofEpochMilli(event.endAt.toInstant(eventTz).toEpochMilliseconds())
        .atZone(safeZoneId(event.endAt.timeZone))
    val range = "${fmt.format(startZoned)} - ${fmt.format(endZoned)}"
    val shareText = buildString {
        appendLine(event.title)
        appendLine(range)
        if (!event.location.isNullOrBlank()) {
            appendLine(event.location)
        }
        if (event.attendees.isNotEmpty()) {
            appendLine("Attendees: ${event.attendees.mapNotNull { it.name ?: it.email }.joinToString(", ")}")
        }
        if (!event.description.isNullOrBlank()) {
            appendLine(event.description)
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share event"))
                    }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = event.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(text = range, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!event.location.isNullOrBlank()) Text(text = event.location, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    if (event.attendees.isNotEmpty()) {
                        Text(text = "Attendees:", fontWeight = FontWeight.Bold)
                        event.attendees.forEach { att ->
                            Text(text = "• ${att.name ?: att.email}")
                        }
                        HorizontalDivider()
                    }
                    if (!event.description.isNullOrBlank()) {
                        Text(text = "Description:", fontWeight = FontWeight.Bold)
                        Text(text = event.description)
                    }
                }
            }
        }
    }
}