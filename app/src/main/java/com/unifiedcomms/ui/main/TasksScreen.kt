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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Add
import androidx.compose.material3.icons.filled.ArrowBack
import androidx.compose.material3.icons.filled.Check
import androidx.compose.material3.icons.filled.CheckBox
import androidx.compose.material3.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.icons.filled.Delete
import androidx.compose.material3.icons.filled.Edit
import androidx.compose.material3.icons.filled.FilterList
import androidx.compose.material3.icons.filled.Star
import androidx.compose.material3.icons.filled.Today
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
fun TasksScreen(
    viewModel: MainViewModel,
    onCreateTask: () -> Unit,
    onTaskClick: (MockTask) -> Unit
) {
    val filter by remember { mutableStateOf(TaskFilter.ALL) }
    val tasks = remember { mutableStateOf<List<MockTask>>(getMockTasks()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* Filter */ }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = onCreateTask) {
                        Icon(Icons.Default.Add, contentDescription = "New Task")
                    }
                }
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = onCreateTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create task")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Filter tabs
            Surface(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskFilter.values().forEach { f ->
                        androidx.compose.material3.Chip(
                            onClick = { filter = f },
                            selected = filter == f,
                            label = { Text(f.label) }
                        )
                    }
                }
            }
            
            // Task list
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks.value.filter { filterMatches(it, filter) }) { task ->
                    TaskItem(
                        task = task,
                        onClick = { onTaskClick(it) },
                        onToggle = { tasks.value = tasks.value.map { if (it.id == task.id) it.copy(isCompleted = !it.isCompleted) else it } },
                        onStarToggle = { tasks.value = tasks.value.map { if (it.id == task.id) it.copy(isStarred = !it.isStarred) else it } },
                        onDelete = { tasks.value = tasks.value.filter { it.id != task.id } }
                    )
                    Divider()
                }
            }
        }
    }
}

enum class TaskFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    STARRED("Starred"),
    OVERDUE("Overdue"),
    TODAY("Today")
}

fun filterMatches(task: MockTask, filter: TaskFilter): Boolean = when (filter) {
    TaskFilter.ALL -> true
    TaskFilter.ACTIVE -> !task.isCompleted
    TaskFilter.COMPLETED -> task.isCompleted
    TaskFilter.STARRED -> task.isStarred
    TaskFilter.OVERDUE -> task.isOverdue && !task.isCompleted
    TaskFilter.TODAY -> task.dueDate != null && task.dueDate == java.time.LocalDate.now() && !task.isCompleted
}

@Composable
fun TaskItem(
    task: MockTask,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onStarToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        elevation = 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (task.isCompleted) "Mark incomplete" : "Mark complete",
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Task content
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                    )
                    if (task.isStarred) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Star, contentDescription = "Starred", tint = MaterialTheme.colorScheme.primary, size = 16.sp)
                    }
                }
                
                if (task.dueDate != null) {
                    val isOverdue = task.dueDate!! < java.time.LocalDate.now() && !task.isCompleted
                    Text(
                        text = "Due: ${task.dueDate}",
                        fontSize = 12.sp,
                        color = if (isOverdue) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (task.hasSubtasks) {
                    Text(text = "${task.completedSubtasks}/${task.totalSubtasks} subtasks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Priority indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(task.priorityColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

data class MockTask(
    val id: String,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val isStarred: Boolean = false,
    val dueDate: java.time.LocalDate? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val hasSubtasks: Boolean = false,
    val totalSubtasks: Int = 0,
    val completedSubtasks: Int = 0,
    val listName: String = "Personal"
)

enum class TaskPriority(val color: Int) {
    LOW(0xFF81C784),      // Green
    NORMAL(0xFF64B5F6),   // Blue
    HIGH(0xFFFFB74D),     // Orange
    URGENT(0xFFE57373)    // Red
}

val MockTask.priorityColor: Int
    get() = priority.color

fun getMockTasks(): List<MockTask> = listOf(
    MockTask("1", "Review pull request #42", "Check the new sync engine implementation", false, false, java.time.LocalDate.now().plusDays(1), TaskPriority.HIGH),
    MockTask("2", "Buy groceries", "Milk, eggs, bread, fruits", false, true, java.time.LocalDate.now(), TaskPriority.NORMAL),
    MockTask("3", "Schedule dentist appointment", "Call Dr. Smith's office", false, false, java.time.LocalDate.now().plusDays(3), TaskPriority.LOW),
    MockTask("4", "Finish project proposal", "Complete the Q4 budget proposal", true, false, java.time.LocalDate.now().minusDays(2), TaskPriority.URGENT),
    MockTask("5", "Call mom", "Check in on her", false, false, java.time.LocalDate.now(), TaskPriority.NORMAL),
    MockTask("6", "Refactor sync engine", "Move to Kotlin Flow and Room", false, false, java.time.LocalDate.now().plusDays(7), TaskPriority.HIGH, true, 5, 2, "Work"),
    MockTask("7", "Plan weekend trip", "Research destinations and book hotel", false, true, java.time.LocalDate.now().plusDays(10), TaskPriority.LOW),
)

@Composable
fun CreateTaskScreen(
    viewModel: MainViewModel,
    onSave: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<java.time.LocalDate?>(java.time.LocalDate.now().plusDays(1)) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    var listName by remember { mutableStateOf("Personal") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Task") },
                navigationIcon = { IconButton(onClick = onSave) { Icon(Icons.Default.ArrowBack, contentDescription = "Cancel") } },
                actions = { IconButton(onClick = onSave) { Icon(Icons.Default.Save, contentDescription = "Save") } }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.material3.TextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.TextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            
            Text(text = "Due Date", fontWeight = FontWeight.Bold)
            androidx.compose.material3.DatePickerDialog(onDismissRequest = {}, date = dueDate ?: java.time.LocalDate.now(), onDateSelected = { dueDate = it })
            
            Text(text = "Priority", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.values().forEach { p ->
                    androidx.compose.material3.Chip(
                        onClick = { priority = p },
                        selected = priority == p,
                        label = { Text(p.name) }
                    )
                }
            }
            
            androidx.compose.material3.TextField(value = listName, onValueChange = { listName = it }, label = { Text("List") }, modifier = Modifier.fillMaxWidth())
        }
    }
}