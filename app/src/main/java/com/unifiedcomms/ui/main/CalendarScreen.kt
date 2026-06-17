package com.unifiedcomms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer

@Composable
fun CalendarScreen(
    viewModel: MainViewModel,
    onCreateEvent: () -> Unit,
    onEventClick: (MockEvent) -> Unit
) {
    val selectedView by remember { mutableStateOf(CalendarView.MONTH) }
    val currentDate = remember { mutableStateOf(java.time.LocalDate.now()) }

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
                CalendarView.DAY -> DayView(currentDate.value, onEventClick)
                CalendarView.WEEK -> WeekView(currentDate.value, onEventClick)
                CalendarView.MONTH -> MonthView(currentDate.value, onEventClick)
            }
        }
    }
}

enum class CalendarView { DAY, WEEK, MONTH }

@Composable
fun DayView(date: java.time.LocalDate, onEventClick: (MockEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Day View: ${date.dayOfWeek}, ${date.month} ${date.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Time slots
        (6..22).forEach { hour ->
            val events = getMockEventsForDate(date).filter { it.startHour == hour }
            if (events.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row {
                            Text(text = String.format("%02d:00", hour), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        events.forEach { event ->
                            EventChip(event = event, onClick = { onEventClick(it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekView(date: java.time.LocalDate, onEventClick: (MockEvent) -> Unit) {
    val weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (0..6).forEach { dayOffset ->
                val day = weekStart.plusDays(dayOffset)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(text = day.dayOfWeek.name.take(3), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(text = day.dayOfMonth.toString(), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val dayEvents = getMockEventsForDate(day)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayEvents.take(3).forEach { event ->
                            EventChip(event = event, onClick = { onEventClick(it) })
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

@Composable
fun MonthView(date: java.time.LocalDate, onEventClick: (MockEvent) -> Unit) {
    val firstOfMonth = date.withDayOfMonth(1)
    val dayOfWeekOffset = firstOfMonth.dayOfWeek.value - 1 // Monday = 0
    val daysInMonth = firstOfMonth.lengthOfMonth()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(text = day, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        // Calendar grid
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
                            firstOfMonth.plusDays(day - 1).also { day++ }
                        } else {
                            null
                        }
                        
                        val events = cellDate?.let { getMockEventsForDate(it) } ?: emptyList()
                        
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
                            onClick = { events.firstOrNull()?.let { onEventClick(it) } }
                        ) {
                            Column(
                                modifier = Modifier.padding(4.dp).fillMaxSize(),
                                verticalArrangement = Arrangement.Top
                            ) {
                                Text(text = cellDate?.dayOfMonth.toString() ?: "", fontSize = 12.sp, fontWeight = if (cellDate == java.time.LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    events.take(3).forEach { event ->
                                        EventChip(event = event, compact = true, onClick = { onEventClick(it) })
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
    val color: Int,
    val calendarName: String,
    val isAllDay: Boolean = false
)

fun getMockEventsForDate(date: java.time.LocalDate): List<MockEvent> {
    val hash = date.toString().hashCode().absoluteValue
    val count = (hash % 4) + 1
    return (0 until count).map { i ->
        val colors = listOf(0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFFBA68C8)
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

@Composable
fun EventChip(
    event: MockEvent,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 4.dp)
            .background(Color(event.color), RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = if (compact) 4.dp else 8.dp)) {
            Text(
                text = if (!compact) "${String.format("%02d:00", event.startHour)}-${String.format("%02d:00", event.endHour)} ${event.title}" else event.title,
                fontSize = if (compact) 10.sp else 12.sp,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(text = event.calendarName, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun CreateEventScreen(
    viewModel: MainViewModel,
    onSave: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    var startTime by remember { mutableStateOf(java.time.LocalTime.of(10, 0)) }
    var endTime by remember { mutableStateOf(java.time.LocalTime.of(11, 0)) }
    var isAllDay by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(0xFFE57373) }
    var attendees by remember { mutableStateOf("") }
    var reminderMinutes by remember { mutableStateOf(60) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Event") },
                navigationIcon = { IconButton(onClick = onSave) { Icon(Icons.Default.ArrowBack, contentDescription = "Cancel") } },
                actions = {
                    IconButton(onClick = { /* Save event */ onSave() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        androidx.compose.material3.ScrollableColumn(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize()
        ) {
            androidx.compose.material3.TextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.TextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            androidx.compose.material3.TextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.material3.DatePickerDialog(onDismissRequest = {}, date = selectedDate, onDateSelected = { selectedDate = it })
                androidx.compose.material3.TimePickerDialog(onDismissRequest = {}, time = startTime, onTimeSelected = { startTime = it })
                androidx.compose.material3.TimePickerDialog(onDismissRequest = {}, time = endTime, onTimeSelected = { endTime = it })
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.material3.Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it })
                Text("All day")
            }
            
            // Color picker
            Text(text = "Calendar Color", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFFBA68C8, 0xFF4FC3F7, 0xFF4DB6AC, 0xFFAED581).forEach { color ->
                    Surface(
                        modifier = Modifier.size(32.dp).background(if (selectedColor == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(16.dp)).padding(4.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(color),
                        onClick = { selectedColor = color }
                    ) { Spacer(modifier = Modifier.fillMaxSize()) }
                }
            }
            
            // Attendees
            androidx.compose.material3.TextField(value = attendees, onValueChange = { attendees = it }, label = { Text("Attendees (comma-separated emails)") }, modifier = Modifier.fillMaxWidth())
            
            // Reminder
            androidx.compose.material3.DropdownMenu(
                expanded = true,
                onDismissRequest = {}
            ) {
                androidx.compose.material3.DropdownMenuItem(onClick = { reminderMinutes = 0 }) { Text("At time of event") }
                androidx.compose.material3.DropdownMenuItem(onClick = { reminderMinutes = 5 }) { Text("5 minutes before") }
                androidx.compose.material3.DropdownMenuItem(onClick = { reminderMinutes = 15 }) { Text("15 minutes before") }
                androidx.compose.material3.DropdownMenuItem(onClick = { reminderMinutes = 30 }) { Text("30 minutes before") }
                androidx.compose.material3.DropdownMenuItem(onClick = { reminderMinutes = 60 }) { Text("1 hour before") }
                androidx.compose.material3.DropdownMenuItem(onClick = { reminderMinutes = 1440 }) { Text("1 day before") }
            }
        }
    }
}

@Composable
fun EventDetailScreen(
    viewModel: MainViewModel,
    eventId: String,
    onEdit: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = { IconButton(onClick = onEdit) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { /* Share */ }) { Icon(Icons.Default.Share, contentDescription = "Share") }
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
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Team Meeting", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Tomorrow, 10:00 AM - 11:00 AM", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Conference Room A", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Divider()
                    Text(text = "Attendees:", fontWeight = FontWeight.Bold)
                    listOf("alice@company.com", "bob@company.com", "charlie@company.com").forEach { email ->
                        Text(text = "• $email")
                    }
                    Divider()
                    Text(text = "Description:", fontWeight = FontWeight.Bold)
                    Text(text = "Weekly team sync to discuss project progress and blockers.")
                }
            }
        }
    }
}