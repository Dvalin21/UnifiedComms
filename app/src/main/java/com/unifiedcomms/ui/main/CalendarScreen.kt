package com.unifiedcomms.ui.main
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.datetime.TimeZone
import java.time.ZoneId
import com.unifiedcomms.data.model.CalendarEvent
import kotlinx.coroutines.launch

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
    val allEvents by viewModel.calendarRepository.getUnifiedEvents(activeAccountIds)
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
                Icon(Icons.Default.Event, contentDescription = "Create event")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedView) {
                CalendarView.DAY -> DayView(date = currentDate.value, events = allEvents, onEventClick = onEventClick)
                CalendarView.WEEK -> WeekView(date = currentDate.value, events = allEvents, onEventClick = onEventClick)
                CalendarView.MONTH -> MonthView(date = currentDate.value, allEvents = allEvents, onEventClick = onEventClick)
            }
        }
    }
}

enum class CalendarView { DAY, WEEK, MONTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayView(date: java.time.LocalDate, events: List<CalendarEvent>, onEventClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Day View: ${date.dayOfWeek}, ${date.month} ${date.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        val dayEvents = events.filter { isSameDay(it.startAt.toInstant(TimeZone.of(it.startAt.timeZone)), date) }
        if (dayEvents.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayEvents.forEach { event ->
                        EventChip(event = event.toMockEvent(), onClick = { onEventClick(event.id) })
                    }
                }
            }
        }
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthView(date: java.time.LocalDate, allEvents: List<CalendarEvent>, onEventClick: (String) -> Unit) {
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
                            allEvents.filter { ev -> isSameDay(ev.startAt.toInstant(TimeZone.of(ev.startAt.timeZone)), date) }
                        } ?: emptyList()

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    if (cellDate == java.time.LocalDate.now()) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(4.dp),
                            shape = RoundedCornerShape(8.dp),
                            onClick = { val first = events.firstOrNull(); if (first != null) onEventClick(first.id) }
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.Top) {
                                Text(text = cellDate?.dayOfMonth?.toString() ?: "", fontSize = 12.sp, fontWeight = if (cellDate == java.time.LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    events.take(3).forEach { event ->
                                        EventChip(event = event.toMockEvent(), compact = true, onClick = { onEventClick(event.id) })
                                    }
                                    if (events.size > 3) {
                                        Text(text = "+${events.size - 3}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun isSameDay(instant: kotlinx.datetime.Instant, date: java.time.LocalDate): Boolean {
    return java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(ZoneId.systemDefault())
        .toLocalDate() == date
}

private fun com.unifiedcomms.data.model.CalendarEvent.toMockEvent(): MockEvent = MockEvent(
    id = id,
    title = title,
    startHour = java.time.Instant.ofEpochMilli(startAt.toInstant().toEpochMilliseconds())
        .atZone(java.time.ZoneId.of(startAt.timeZone)).hour,
    endHour = java.time.Instant.ofEpochMilli(endAt.toInstant().toEpochMilliseconds())
        .atZone(java.time.ZoneId.of(endAt.timeZone)).hour,
    color = color.toColorInt().toLong(),
    calendarName = title,
    isAllDay = startAt.isAllDay
)

@Composable
fun EventChip(event: MockEvent, compact: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 4.dp)
            .background(
                Color(event.color),
                RoundedCornerShape(6.dp)
            ),
        shape = RoundedCornerShape(6.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = if (compact) 4.dp else 8.dp)) {
            Text(
                text = if (!compact) "${String.format("%02d:00", event.startHour)}-${String.format("%02d:00", event.endHour)} ${event.title}" else event.title,
                fontSize = if (compact) 10.sp else 12.sp,
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
                                val event = com.unifiedcomms.data.model.CalendarEvent(
                                    accountId = accountId,
                                    calendarId = accountId,
                                    uid = java.util.UUID.randomUUID().toString(),
                                    title = title,
                                    description = description.takeIf { it.isNotBlank() },
                                    location = location.takeIf { it.isNotBlank() },
                                    startAt = com.unifiedcomms.data.model.EventDateTime(
                                        dateTime = kotlinx.datetime.LocalDateTime(
                                            selectedDate.year,
                                            selectedDate.monthValue,
                                            selectedDate.dayOfMonth,
                                            9,
                                            0
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
                                            10,
                                            0
                                        ),
                                        date = kotlinx.datetime.LocalDate(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                                        timeZone = kotlinx.datetime.TimeZone.currentSystemDefault().id,
                                        isAllDay = isAllDay
                                    ),
                                    color = com.unifiedcomms.data.model.EventColor.fromInt(selectedColor.toInt())
                                )
                                viewModel.calendarRepository.insertEvent(event)
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
    onBack: () -> Unit,
    onShare: () -> Unit = {}
) {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    val startZoned = java.time.Instant.ofEpochMilli(event.startAt.toInstant(kotlinx.datetime.TimeZone.of(event.timezone)).toEpochMilliseconds())
        .atZone(java.time.ZoneId.of(event.timezone))
    val endZoned = java.time.Instant.ofEpochMilli(event.endAt.toInstant(kotlinx.datetime.TimeZone.of(event.timezone)).toEpochMilliseconds())
        .atZone(java.time.ZoneId.of(event.timezone))
    val range = "${fmt.format(startZoned)} - ${fmt.format(endZoned)}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share") }
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