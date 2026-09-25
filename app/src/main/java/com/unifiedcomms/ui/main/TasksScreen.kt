package com.unifiedcomms.ui.main
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    viewModel: MainViewModel,
    onCreateTask: () -> Unit,
    onTaskClick: (MockTask) -> Unit
) {
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccountIds = accounts.filter { it.isActive }.map { it.id }
    val taskFlow = if (activeAccountIds.isEmpty()) kotlinx.coroutines.flow.flowOf<List<com.unifiedcomms.data.model.Task>>(emptyList())
    else viewModel.taskRepository.getAllUnified(activeAccountIds)
    val tasks by taskFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val displayTasks = remember(tasks) { tasks.map { it.toMockTask() } }
    var taskToDelete by remember { mutableStateOf<MockTask?>(null) }
    var taskError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // ponytail: plain header Row, not a nested Scaffold+TopAppBar. The parent screen already
    // draws the app bar and applies its insets, so a second bar just added a dead band of
    // space above the title.
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onCreateTask) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New")
            }
        }
        taskError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TaskFilter.values().forEach { f ->
                    FilterChip(
                        onClick = { filter = f },
                        selected = filter == f,
                        label = { Text(f.label, maxLines = 1, softWrap = false) }
                    )
                }
            }
        }

        val filtered = displayTasks.filter { filterMatches(it, filter) }
        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "No tasks yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tasks you add or sync will show up here.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            items(filtered) { task ->
                TaskItem(
                    task = task,
                    onClick = { onTaskClick(task) },
                    onToggle = {
                        coroutineScope.launch {
                            tasks.firstOrNull { it.id == task.id }?.let { modelTask ->
                                val result = viewModel.setTaskCompleted(modelTask, !task.isCompleted)
                                taskError = if (result.success) null else result.errorMessage
                            }
                        }
                    },
                    onDelete = { taskToDelete = task }
                 )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        }
    }
    taskToDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete task?") },
            text = { Text(pending.title) },
            confirmButton = {
                TextButton(onClick = {
                    taskToDelete = null
                    coroutineScope.launch {
                        tasks.firstOrNull { it.id == pending.id }?.let { modelTask ->
                            val result = viewModel.deleteTask(modelTask)
                            taskError = if (result.success) null else result.errorMessage
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

enum class TaskFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    OVERDUE("Overdue"),
    TODAY("Today")
}

fun filterMatches(task: MockTask, filter: TaskFilter): Boolean = when (filter) {
    TaskFilter.ALL -> true
    TaskFilter.ACTIVE -> !task.isCompleted
    TaskFilter.COMPLETED -> task.isCompleted
    TaskFilter.OVERDUE -> task.isOverdue && !task.isCompleted
    TaskFilter.TODAY -> task.dueDate != null && task.dueDate == LocalDate.now() && !task.isCompleted
}

@Composable
fun TaskItem(
    task: MockTask,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val priorityColor = when (task.priority) {
        TaskPriority.NONE -> Color(0xFF9E9E9E)
        TaskPriority.LOW -> Color(0xFF81C784)
        TaskPriority.NORMAL -> Color(0xFF64B5F6)
        TaskPriority.HIGH -> Color(0xFFFFB74D)
        TaskPriority.URGENT -> Color(0xFFE57373)
    }

    // ponytail: flat row + divider, matching the mail list. A bordered card per task read as
    // boxes-inside-boxes and, once the stroke was dropped, the surface tint was invisible on
    // the dark background anyway — so it was a card that never looked like a card.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (task.isCompleted) "Mark incomplete" else "Mark complete",
                tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (task.priority != TaskPriority.NONE) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(priorityColor, CircleShape)
                    )
                }
            }

            if (task.dueDate != null) {
                val isOverdue = task.dueDate < LocalDate.now() && !task.isCompleted
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

        onDelete?.let {
            IconButton(onClick = it) {
                Icon(Icons.Default.Delete, contentDescription = "Delete task")
            }
        }
    }
}

data class MockTask(
    val id: String,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val dueDate: LocalDate? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val hasSubtasks: Boolean = false,
    val totalSubtasks: Int = 0,
    val completedSubtasks: Int = 0,
    val listName: String = "Personal"
)

enum class TaskPriority(val color: Color) {
    NONE(Color(0xFF9E9E9E)),
    LOW(Color(0xFF81C784)),
    NORMAL(Color(0xFF64B5F6)),
    HIGH(Color(0xFFFFB74D)),
    URGENT(Color(0xFFE57373))
}

val MockTask.isOverdue: Boolean
    get() = dueDate != null && dueDate < LocalDate.now() && !isCompleted

val MockTask.priorityColor: Color
    get() = priority.color

private fun com.unifiedcomms.data.model.Task.toMockTask(): MockTask = MockTask(
    id = id,
    title = title,
    description = description,
    isCompleted = status == com.unifiedcomms.data.model.TaskStatus.COMPLETED,
    dueDate = dueAt?.date?.let { java.time.LocalDate.parse(it.toString()) }
        ?: dueAt?.dateTime?.let { java.time.LocalDateTime.parse(it.toString()).toLocalDate() },
    priority = when (priority) {
        com.unifiedcomms.data.model.TaskPriority.NONE -> TaskPriority.NONE
        com.unifiedcomms.data.model.TaskPriority.LOW -> TaskPriority.LOW
        com.unifiedcomms.data.model.TaskPriority.MEDIUM -> TaskPriority.NORMAL
        com.unifiedcomms.data.model.TaskPriority.HIGH -> TaskPriority.HIGH
        com.unifiedcomms.data.model.TaskPriority.URGENT -> TaskPriority.URGENT
    },
    hasSubtasks = hasSubtasks,
    totalSubtasks = subtaskCount,
    completedSubtasks = completedSubtaskCount,
    listName = listId
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    viewModel: MainViewModel,
    accountId: String,
    taskId: String? = null,
    onSave: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var originalDueAt by remember { mutableStateOf<com.unifiedcomms.data.model.TaskDateTime?>(null) }
    var dueDateChanged by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    var listName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(taskId) {
        if (taskId != null) {
            val existing = viewModel.getTaskById(taskId)
            if (existing != null) {
                title = existing.title
                description = existing.description ?: ""
                originalDueAt = existing.dueAt
                dueDate = existing.dueAt?.date?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) }
                    ?: existing.dueAt?.dateTime?.date?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) }
                dueDateChanged = false
                priority = when (existing.priority) {
                    com.unifiedcomms.data.model.TaskPriority.NONE -> TaskPriority.NONE
                    com.unifiedcomms.data.model.TaskPriority.LOW -> TaskPriority.LOW
                    com.unifiedcomms.data.model.TaskPriority.MEDIUM -> TaskPriority.NORMAL
                    com.unifiedcomms.data.model.TaskPriority.HIGH -> TaskPriority.HIGH
                    com.unifiedcomms.data.model.TaskPriority.URGENT -> TaskPriority.URGENT
                }
                listName = existing.listId
            }
        }
    }

    val duePickerState = rememberDatePickerState(
        initialSelectedDateMillis = (dueDate ?: LocalDate.now()).toEpochDay() * 86_400_000L
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == null) "Create Task" else "Edit Task") },
                navigationIcon = { IconButton(onClick = onSave) { Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Cancel") } },
                actions = {
                    IconButton(onClick = {
                        if (title.isBlank()) {
                            saveError = "Title is required"
                        } else {
                            coroutineScope.launch {
                                saveError = null
                                val existing = taskId?.let { viewModel.getTaskById(it) }
                                val resolvedAccountId = existing?.accountId
                                    ?: accountId.takeIf { it.isNotBlank() }
                                    ?: viewModel.getDefaultAccount()?.id
                                    ?: viewModel.getActiveAccounts().firstOrNull()?.id
                                    ?: run {
                                        saveError = "No active account"
                                        return@launch
                                    }
                                val mappedPriority = when (priority) {
                                    TaskPriority.NONE -> com.unifiedcomms.data.model.TaskPriority.NONE
                                    TaskPriority.LOW -> com.unifiedcomms.data.model.TaskPriority.LOW
                                    TaskPriority.NORMAL -> com.unifiedcomms.data.model.TaskPriority.MEDIUM
                                    TaskPriority.HIGH -> com.unifiedcomms.data.model.TaskPriority.HIGH
                                    TaskPriority.URGENT -> com.unifiedcomms.data.model.TaskPriority.URGENT
                                }
                                val dueAt = when {
                                    dueDate == null -> null
                                    !dueDateChanged -> originalDueAt
                                    else -> com.unifiedcomms.data.model.TaskDateTime(
                                        date = kotlinx.datetime.LocalDate(dueDate!!.year, dueDate!!.monthValue, dueDate!!.dayOfMonth),
                                        timeZone = originalDueAt?.timeZone
                                            ?: kotlinx.datetime.TimeZone.currentSystemDefault().id,
                                        hasTime = false
                                    )
                                }
                                val task = existing?.withDueAt(dueAt)?.copy(
                                    title = title,
                                    description = description.takeIf { it.isNotBlank() },
                                    listId = listName,
                                    priority = mappedPriority,
                                    needsSync = true
                                ) ?: com.unifiedcomms.data.model.Task(
                                    accountId = resolvedAccountId,
                                    listId = listName,
                                    uid = java.util.UUID.randomUUID().toString(),
                                    title = title,
                                    description = description.takeIf { it.isNotBlank() },
                                    priority = mappedPriority,
                                    dueAt = dueAt,
                                    isLocalOnly = true,
                                    needsSync = true
                                )
                                val result = viewModel.saveTask(task)
                                if (result.success) onSave()
                                else saveError = result.errorMessage ?: "Task could not be saved"
                            }
                        }
                    }) { Icon(Icons.Default.Save, contentDescription = "Save") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
            TextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            Text(text = "Due Date", fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = {
                dueDate?.let { duePickerState.selectedDateMillis = it.toEpochDay() * 86_400_000L }
                showDueDatePicker = true
            }) {
                Text(dueDate?.toString() ?: "No due date")
            }
            if (dueDate != null) {
                TextButton(onClick = {
                    dueDate = null
                    dueDateChanged = true
                }) { Text("Clear due date") }
            }
            if (showDueDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDueDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            duePickerState.selectedDateMillis?.let {
                                val picked = java.time.LocalDate.ofEpochDay(it / 86_400_000L)
                                if (picked != dueDate) dueDateChanged = true
                                dueDate = picked
                            }
                            showDueDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDueDatePicker = false }) { Text("Cancel") }
                    }
                ) { DatePicker(state = duePickerState) }
            }

            Text(text = "Priority", fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskPriority.values().forEach { p ->
                    FilterChip(
                        onClick = { priority = p },
                        selected = priority == p,
                        label = { Text(p.name, maxLines = 1, softWrap = false) }
                    )
                }
            }

            TextField(value = listName, onValueChange = { listName = it }, label = { Text("List") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
