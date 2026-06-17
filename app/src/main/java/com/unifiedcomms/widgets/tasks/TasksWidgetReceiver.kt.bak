package com.unifiedcomms.widgets.tasks

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.fontWeight
import androidx.glance.text.textAlign
import androidx.glance.text.textStyle
import androidx.glance.Alignment
import androidx.glance.unit.dp
import androidx.glance.unit.sp
import androidx.glance.ColorProvider
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.wear.tiles.provideTileComposition
import androidx.glance.action.StartActivity
import androidx.glance.action.Action
import com.unifiedcomms.R
import com.unifiedcomms.ui.main.MainActivity
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.ui.theme.AccountColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class TasksWidgetReceiver : GlanceAppWidget() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val today = LocalDate.now()
        val tasks = getMockTasksForToday() // Would come from repository
        
        TasksWidgetComposition(
            date = today,
            tasks = tasks,
            onClick = StartActivity(context, Intent(context, MainActivity::class.java))
        )
    }

    private fun getMockTasksForToday(): List<MockTask> {
        return listOf(
            MockTask("1", "Review PR #42", false, TaskPriority.HIGH, "Work"),
            MockTask("2", "Buy groceries", true, TaskPriority.NORMAL, "Personal"),
            MockTask("3", "Call dentist", false, TaskPriority.LOW, "Personal"),
            MockTask("4", "Finish proposal", false, TaskPriority.URGENT, "Work"),
        )
    }

    @Composable
    private fun TasksWidgetComposition(
        date: LocalDate,
        tasks: List<MockTask>,
        onClick: Action
    ) {
        val colorProvider = ColorProviderDefaults()
        val completedCount = tasks.count { it.isCompleted }
        val totalCount = tasks.size
        
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with progress
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Tasks",
                        style = textStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = colorProvider.onSurface)
                    )
                    Text(
                        text = "$completedCount of $totalCount completed",
                        style = textStyle(fontSize = 12.sp, color = colorProvider.onSurfaceVariant)
                    )
                }
                
                // Progress ring (simplified as text)
                Text(
                    text = "${(completedCount * 100 / totalCount.coerceAtLeast(1))}%",
                    style = textStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorProvider.primary)
                )
            }

            // Tasks list
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tasks.take(4).forEach { task ->
                    Surface(
                        modifier = GlanceModifier.fillMaxWidth()
                            .background(
                                if (task.isCompleted) colorProvider.surfaceContainerHighest 
                                else Color(task.priority.color),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentColor = if (task.isCompleted) colorProvider.onSurface else Color.White
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = GlanceModifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Checkbox
                                Box(
                                    modifier = GlanceModifier.size(20.dp)
                                        .background(
                                            if (task.isCompleted) Color(0xFF4CAF50) 
                                            else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            if (task.isCompleted) 0.dp else 2.dp,
                                            if (task.isCompleted) Color.Transparent else colorProvider.outline,
                                            RoundedCornerShape(4.dp)
                                        )
                                ) {
                                    if (task.isCompleted) {
                                        androidx.glance.Text(
                                            text = "✓",
                                            style = textStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                
                                Column {
                                    Text(
                                        text = task.title,
                                        style = textStyle(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = if (task.isCompleted) colorProvider.onSurfaceVariant else Color.White
                                        )
                                    )
                                    Text(
                                        text = task.listName,
                                        style = textStyle(fontSize = 11.sp, color = if (task.isCompleted) colorProvider.onSurfaceVariant else Color.White.copy(alpha = 0.7f))
                                    )
                                }
                            }
                            
                            if (!task.isCompleted) {
                                // Priority indicator
                                Box(
                                    modifier = GlanceModifier
                                        .width(4.dp)
                                        .height(24.dp)
                                        .background(Color(task.priority.color), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }
                
                if (tasks.size > 4) {
                    Text(
                        text = "+ ${tasks.size - 4} more tasks",
                        style = textStyle(fontSize = 12.sp, color = colorProvider.onSurfaceVariant),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Quick actions
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    label = "New Task",
                    color = colorProvider.primary,
                    onClick = onClick
                )
                ActionButton(
                    label = "All Tasks",
                    color = colorProvider.secondary,
                    onClick = onClick
                )
            }
        }
    }

    @Composable
    private fun ActionButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: Action) {
        androidx.glance.Text(
            text = label,
            modifier = GlanceModifier
                .fillMaxWidth()
                .weight(1f)
                .background(color, RoundedCornerShape(8.dp))
                .padding(vertical = 12.dp)
                .clickable(onClick),
            textAlign = TextAlign.Center,
            style = textStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
        )
    }
}

data class MockTask(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val priority: TaskPriority,
    val listName: String
)

enum class TaskPriority(val color: Int) {
    LOW(0xFF81C784),
    NORMAL(0xFF64B5F6),
    HIGH(0xFFFFB74D),
    URGENT(0xFFE57373)
}

@Composable
fun provideGlance(context: Context, glanceId: GlanceId) {
    TasksWidgetReceiver().provideGlance(context, glanceId)
}