package com.unifiedcomms.widgets.email

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
import com.unifiedcomms.data.repository.EmailRepository
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.ui.theme.AccountColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

class EmailWidgetReceiver : GlanceAppWidget() {

    @Inject
    lateinit var emailRepo: EmailRepository

    @Composable
    override fun Content() {
        val context = LocalContext.current
        
        EmailWidgetComposition(
            accounts = emptyList(), // Would come from repository
            unreadCounts = emptyMap(),
            onClick = StartActivity(context, Intent(context, MainActivity::class.java))
        )
    }

    @Composable
    private fun EmailWidgetComposition(
        accounts: List<Account>,
        unreadCounts: Map<String, Int>,
        onClick: Action
    ) {
        val colorProvider = ColorProviderDefaults()
        
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "UnifiedComms",
                    style = textStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorProvider.onSurface)
                )
                Text(
                    text = "Mail",
                    style = textStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colorProvider.primary)
                )
            }

            // Account summary
            if (accounts.isEmpty()) {
                Surface(
                    modifier = GlanceModifier.fillMaxWidth()
                        .background(colorProvider.surfaceContainerHighest)
                        .padding(24.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "No accounts configured",
                        style = textStyle(fontSize = 14.sp, color = colorProvider.onSurfaceVariant),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { account ->
                        val color = AccountColors.getColorForAccount(account.id)
                        val unread = unreadCounts[account.id] ?: 0
                        
                        Surface(
                            modifier = GlanceModifier.fillMaxWidth()
                                .background(Color(color.container))
                                .padding(12.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentColor = Color(color.onContainer)
                        ) {
                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = account.name,
                                        style = textStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    )
                                    if (unread > 0) {
                                        Text(
                                            text = "$unread",
                                            style = textStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                            modifier = GlanceModifier
                                                .background(Color(0xFFE57373), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = account.email,
                                    style = textStyle(fontSize = 12.sp, color = colorProvider.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }
            }

            // Quick actions
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    label = "Compose",
                    color = colorProvider.primary,
                    onClick = onClick
                )
                ActionButton(
                    label = "Inbox",
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

    companion object {
        fun updateWidget(context: Context, widgetId: Int) {
            val glanceId = GlanceId(widgetId)
            // Trigger widget update
        }
    }
}

@Composable
fun provideGlance(context: Context, glanceId: GlanceId) {
    EmailWidgetReceiver().provideGlance(context, glanceId)
}