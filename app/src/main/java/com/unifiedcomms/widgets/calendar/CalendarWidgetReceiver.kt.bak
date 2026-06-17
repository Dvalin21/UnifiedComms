package com.unifiedcomms.widgets.calendar

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.layout.Box
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
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.ui.theme.AccountColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class CalendarWidgetReceiver : GlanceAppWidget() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val today = LocalDate.now()
        val events = getMockEventsForToday() // Would come from repository
        
        CalendarWidgetComposition(
            date = today,
            events = events,
            onClick = StartActivity(context, Intent(context, MainActivity::class.java))
        )
    }

    private fun getMockEventsForToday(): List<MockEvent> {
        // Mock data - would come from repository in real implementation
        return listOf(
            MockEvent("1", "Team Standup", 9, 10, 0xFF64B5F6, "Work"),
            MockEvent("2", "Lunch with Sarah", 12, 13, 0xFF81C784, "Personal"),
            MockEvent("3", "Project Review", 14, 15, 0xFFE57373, "Work"),
            MockEvent("4", "Gym", 18, 19, 0xFFFFB74D, "Health"),
        )
    }

    @Composable
    private fun CalendarWidgetComposition(
        date: LocalDate,
        events: List<MockEvent>,
        onClick: Action
    ) {
        val colorProvider = ColorProviderDefaults()
        
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with date
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = date.dayOfWeek.toString(),
                        style = textStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = colorProvider.onSurface)
                    )
                    Text(
                        text = "${date.month} ${date.dayOfMonth}, ${date.year}",
                        style = textStyle(fontSize = 12.sp, color = colorProvider.onSurfaceVariant)
                    )
                }
                Text(
                    text = "Calendar",
                    style = textStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colorProvider.primary)
                )
            }

            // Events
            if (events.isEmpty()) {
                Surface(
                    modifier = GlanceModifier.fillMaxWidth()
                        .background(colorProvider.surfaceContainerHighest)
                        .padding(24.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "No events today 🎉",
                        style = textStyle(fontSize = 16.sp, color = colorProvider.onSurfaceVariant),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    events.take(4).forEach { event ->
                        Surface(
                            modifier = GlanceModifier.fillMaxWidth()
                                .background(Color(event.color))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentColor = Color.White
                        ) {
                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = event.title,
                                        style = textStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    )
                                    Text(
                                        text = "${String.format("%02d:%02d", event.startHour, 0)} - ${String.format("%02d:%02d", event.endHour, 0)}",
                                        style = textStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                    )
                                }
                                Text(
                                    text = event.calendarName,
                                    style = textStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                                )
                            }
                        }
                    }
                    
                    if (events.size > 4) {
                        Text(
                            text = "+ ${events.size - 4} more events",
                            style = textStyle(fontSize = 12.sp, color = colorProvider.onSurfaceVariant),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Quick actions
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    label = "New Event",
                    color = colorProvider.primary,
                    onClick = onClick
                )
                ActionButton(
                    label = "Month View",
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

data class MockEvent(
    val id: String,
    val title: String,
    val startHour: Int,
    val endHour: Int,
    val color: Int,
    val calendarName: String
)

@Composable
fun provideGlance(context: Context, glanceId: GlanceId) {
    CalendarWidgetReceiver().provideGlance(context, glanceId)
}