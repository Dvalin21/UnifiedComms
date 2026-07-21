package com.unifiedcomms.ui.main
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch
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
    // raw masters only, so repeats never appeared). Window recomputed on view/date change.
    val eventWindow = remember(selectedView, currentDate.value) {
        val d = currentDate.value
        val z = java.time.ZoneId.systemDefault()
        val startOfDay = { day: java.time.LocalDate -> day.atStartOfDay(z).toInstant().toEpochMilli() }
        when (selectedView) {
            CalendarView.DAY -> startOfDay(d) to startOfDay(d.plusDays(1))
            CalendarView.WEEK -> startOfDay(d.minusDays(7)) to startOfDay(d.plusDays(7))
            CalendarView.MONTH -> startOfDay(d.withDayOfMonth(1).minusWeeks(1)) to startOfDay(d.withDayOfMonth(d.lengthOfMonth()).plusWeeks(1))
        }
    }
    val allEvents by (if (activeAccountIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
    else viewModel.calendarRepository.getEventsInRangeUnified(activeAccountIds, eventWindow.first, eventWindow.second))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(currentDate.value.toString(), fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = { currentDate.value = java.time.LocalDate.now() }) {
                        Icon(Icons.Default.Today, contentDescription = "Today")
                    }
                    IconButton(onClick = { selectedView = CalendarView.DAY }) {
                        Icon(Icons.Default.CalendarViewDay, contentDescription = "Day view", tint = if (selectedView == CalendarView.DAY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { selectedView = CalendarView.WEEK }) {
                        Icon(Icons.Default.CalendarViewWeek, contentDescription = "Week view", tint = if (selectedView == CalendarView.WEEK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { selectedView = CalendarView.MONTH }) {
                        Icon(Icons.Default.CalendarViewMonth, contentDescription = "Month view", tint = if (selectedView == CalendarView.MONTH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onCreateEvent) {
                        Icon(Icons.Default.Add, contentDescription = "Create event")
                    }
                    }
                )
                },
            floatingActionButton = {
                androidx.compose.material3.FloatingActionButton(
                    onClick = onCreateEvent,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create event")
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (selectedView) {
                CalendarView.DAY -> DayView(date = currentDate.value, events = allEvents, onEventClick = onEventClick)
                CalendarView.WEEK -> WeekView(date = currentDate.value, events = allEvents, onEventClick = onEventClick)
                CalendarView.MONTH -> MonthView(date = currentDate.value, allEvents = allEvents, onDayClick = { date -> currentDate.value = date; selectedView = CalendarView.DAY })
            }
        }
    }
}

enum class CalendarView { DAY, WEEK, MONTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayView(date: java.time.LocalDate, events: List<CalendarEvent>, onEventClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Day View: ${date.dayOfWeek}, ${date.month} ${date.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(16.dp))

        val dayEvents = events.filter { isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), date) }
        if (dayEvents.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayEvents.forEach { event ->
                        EventChip(event = event.toMockEvent(), onClick = { onEventClick(event.id) })
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        CurrentTimePanel(events = events)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekView(date: java.time.LocalDate, events: List<CalendarEvent>, onEventClick: (String) -> Unit) {
    val weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (0..6).forEach { dayOffset ->
                val day = weekStart.plusDays(dayOffset.toLong())
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(text = day.dayOfWeek.name.take(3), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(text = day.dayOfMonth.toString(), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val dayEvents = events.filter { isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), day) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayEvents.take(3).forEach { event ->
                            EventChip(event = event.toMockEvent(), onClick = { onEventClick(event.id) })
                        }
                        if (dayEvents.size > 3) {
                            Text(text = "+${dayEvents.size - 3} more", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        CurrentTimePanel(events = events)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthView(date: java.time.LocalDate, allEvents: List<CalendarEvent>, onDayClick: (java.time.LocalDate) -> Unit) {
    val firstOfMonth = date.withDayOfMonth(1)
    val dayOfWeekOffset = firstOfMonth.dayOfWeek.value - 1 // Monday = 0
    val daysInMonth = firstOfMonth.lengthOfMonth()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(text = day, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Column {
            var day = 1
            for (week in 0..5) {
                if (day > daysInMonth) break
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0..6).forEach { weekDay ->
                        val cellDate = if (week == 0 && weekDay < dayOfWeekOffset) {
                            null
                        } else if (day <= daysInMonth) {
                            firstOfMonth.plusDays((day - 1).toLong()).also { day++ }
                        } else {
                            null
                        }

                        val events = cellDate?.let { date ->
                            allEvents.filter { ev ->
                                val evZone = java.time.ZoneId.of(com.unifiedcomms.data.model.TimeZoneUtil.normalize(ev.startAt.timeZone) ?: "UTC")
                                isSameDay(ev.startAt.toInstant(com.unifiedcomms.data.model.TimeZoneUtil.toKtxZone(ev.startAt.timeZone)), date, evZone)
                            }
                        } ?: emptyList()

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .background(
                                    if (cellDate == java.time.LocalDate.now()) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(4.dp),
                            shape = RoundedCornerShape(8.dp),
                            onClick = { cellDate?.let { onDayClick(it) } }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.Top) {
                                Text(text = cellDate?.dayOfMonth?.toString() ?: "", fontSize = 12.sp, fontWeight = if (cellDate == java.time.LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    // ponytail: show one colored dot per event (kills the "T..." title
                                    // truncation in ~140px cells); cap at 3 dots, "+n" overflow.
                                    val shown = events.take(3)
                                    shown.forEach { event ->
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(event.color.toColorInt()), CircleShape)
                                        )
                                    }
                                    if (events.size > shown.size) {
                                        Text(text = "+${events.size - shown.size}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        CurrentTimePanel(events = allEvents)
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
    val todayCount = events.count { isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), today) }
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
                    text = now.toLocalDate().format(dateFmt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (todayCount == 0) "No events today" else "$todayCount event${if (todayCount == 1) "" else "s"} today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = now.toLocalTime().format(timeFmt),
                style = MaterialTheme.typography.displaySmall,
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
fun EventChip(event: MockEvent, compact: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.heightIn(min = 18.dp) else Modifier),
        shape = RoundedCornerShape(6.dp),
        color = Color(event.color),
        contentColor = Color.White,
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (compact) 3.dp else 8.dp)) {
            Text(
                text = if (!compact) "${String.format("%02d:00", event.startHour)}-${String.format("%02d:00", event.endHour)} ${event.title}" else event.title,
                fontSize = if (compact) 11.sp else 13.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(text = event.calendarName, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
            }
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
                selectedColor = runCatching { android.graphics.Color.parseColor(ev.color.background) }.getOrNull()?.toLong() ?: selectedColor
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