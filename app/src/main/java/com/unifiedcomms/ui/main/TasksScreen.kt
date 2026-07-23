package com.unifiedcomms.ui.main
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import androidx.compose.material.icons.filled.Star
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
    val tasks by viewModel.taskRepository.getAllUnified(activeAccountIds)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var displayTasks by remember { mutableStateOf<List<MockTask>>(emptyList()) }
    LaunchedEffect(tasks) { displayTasks = tasks.map { it.toMockTask() } }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks", fontWeight = FontWeight.Bold) },
                actions = {
                    // ponytail: the old lone filter IconButton was a dead no-op (onClick = {})
                    // and the Add action lived in a FAB that floated over the list, colliding
                    // with content. Both actions now live as correctly-sized, clearly-labeled
                    // controls in the filter Surface below (see TaskFilter row + Add pill),
                    // matching where the user expects them. No duplicate FAB.
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskFilter.values().forEach { f ->
                        FilterChip(
                            onClick = { filter = f },
                            selected = filter == f,
                            label = {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(f.label, textAlign = TextAlign.Center)
                                }
                            }
                        )
                    }
                    // ponytail: real, correctly-sized "New Task" action placed inline with the
                    // filters (where it reads as a control, not a floating overlay). Pill height
                    // matches the FilterChips so the row stays aligned.
                    OutlinedButton(
                        onClick = onCreateTask,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Task", maxLines = 1, softWrap = false)
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
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { task ->
                    TaskItem(
                        task = task,
                        onClick = { onTaskClick(task) },
                        onToggle = {
                            coroutineScope.launch {
                                viewModel.taskRepository.markCompleted(task.id, !task.isCompleted)
                            }
                        }
                    )
                    HorizontalDivider()
                }
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
    TaskFilter.TODAY -> task.dueDate != null && task.dueDate == LocalDate.now() && !task.isCompleted
}

@Composable
fun TaskItem(
    task: MockTask,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val priorityColor = when (task.priority) {
        TaskPriority.LOW -> Color(0xFF81C784)
        TaskPriority.NORMAL -> Color(0xFF64B5F6)
        TaskPriority.HIGH -> Color(0xFFFFB74D)
        TaskPriority.URGENT -> Color(0xFFE57373)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = if (task.isCompleted) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (task.isCompleted) "Mark incomplete" else "Mark complete",
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

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
                        overflow = TextOverflow.Ellipsis
                    )
                    if (task.isStarred) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Star, contentDescription = "Starred", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
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

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(priorityColor, RoundedCornerShape(2.dp))
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
    val dueDate: LocalDate? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val hasSubtasks: Boolean = false,
    val totalSubtasks: Int = 0,
    val completedSubtasks: Int = 0,
    val listName: String = "Personal"
)

enum class TaskPriority(val color: Color) {
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
    isStarred = false,
    dueDate = dueAt?.date?.let { java.time.LocalDate.parse(it.toString()) }
        ?: dueAt?.dateTime?.let { java.time.LocalDateTime.parse(it.toString()).toLocalDate() },
    priority = when (priority) {
        com.unifiedcomms.data.model.TaskPriority.LOW -> TaskPriority.LOW
        com.unifiedcomms.data.model.TaskPriority.MEDIUM -> TaskPriority.NORMAL
        com.unifiedcomms.data.model.TaskPriority.HIGH -> TaskPriority.HIGH
        com.unifiedcomms.data.model.TaskPriority.URGENT -> TaskPriority.URGENT
        com.unifiedcomms.data.model.TaskPriority.NONE -> TaskPriority.NORMAL
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
    var dueDate by remember { mutableStateOf<java.time.LocalDate?>(java.time.LocalDate.now().plusDays(1)) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    var listName by remember { mutableStateOf("Personal") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(taskId) {
        if (taskId != null) {
            val existing = viewModel.getTaskById(taskId)
            if (existing != null) {
                title = existing.title
                description = existing.description ?: ""
                dueDate = existing.dueAt?.date?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) }
                priority = when (existing.priority) {
                    com.unifiedcomms.data.model.TaskPriority.LOW -> TaskPriority.LOW
                    com.unifiedcomms.data.model.TaskPriority.MEDIUM -> TaskPriority.NORMAL
                    com.unifiedcomms.data.model.TaskPriority.HIGH -> TaskPriority.HIGH
                    com.unifiedcomms.data.model.TaskPriority.URGENT -> TaskPriority.URGENT
                    else -> TaskPriority.NORMAL
                }
                listName = existing.listId
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == null) "Create Task" else "Edit Task") },
                navigationIcon = { IconButton(onClick = onSave) { Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Cancel") } },
                actions = {
                    IconButton(onClick = {
                        if (title.isNotBlank()) {
                            coroutineScope.launch {
                                val task = com.unifiedcomms.data.model.Task(
                                    id = taskId ?: java.util.UUID.randomUUID().toString(),
                                    accountId = accountId,
                                    listId = listName,
                                    uid = taskId ?: java.util.UUID.randomUUID().toString(),
                                    title = title,
                                    description = description.takeIf { it.isNotBlank() },
                                    priority = when (priority) {
                                        TaskPriority.LOW -> com.unifiedcomms.data.model.TaskPriority.LOW
                                        TaskPriority.NORMAL -> com.unifiedcomms.data.model.TaskPriority.MEDIUM
                                        TaskPriority.HIGH -> com.unifiedcomms.data.model.TaskPriority.HIGH
                                        TaskPriority.URGENT -> com.unifiedcomms.data.model.TaskPriority.URGENT
                                    },
                                    dueAt = dueDate?.let { com.unifiedcomms.data.model.TaskDateTime(date = kotlinx.datetime.LocalDate(it.year, it.monthValue, it.dayOfMonth)) }
                                )
                                if (taskId == null) viewModel.taskRepository.insert(task) else viewModel.taskRepository.update(task)
                                onSave()
                            }
                        }
                    }) { Icon(Icons.Default.Save, contentDescription = "Save") }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
            TextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            Text(text = "Due Date", fontWeight = FontWeight.Bold)
            androidx.compose.material3.Text(
                text = dueDate?.toString() ?: LocalDate.now().toString()
            )

            Text(text = "Priority", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.values().forEach { p ->
                    FilterChip(
                        onClick = { priority = p },
                        selected = priority == p,
                        label = { Text(p.name) }
                    )
                }
            }

            TextField(value = listName, onValueChange = { listName = it }, label = { Text("List") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
