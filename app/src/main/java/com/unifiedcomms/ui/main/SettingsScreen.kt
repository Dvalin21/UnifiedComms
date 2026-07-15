package com.unifiedcomms.ui.main
import androidx.compose.foundation.border

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.unifiedcomms.util.PreferencesManager
import android.app.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unifiedcomms.data.model.Account

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onAddAccount: () -> Unit,
    onAccountClick: (Account) -> Unit,
    onBack: () -> Unit,
    onEncryptionClick: () -> Unit = {}
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccounts = accounts.filter { it.isActive }
    var showAbout by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showReminderTime by remember { mutableStateOf(false) }
    var debugLog by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("debug_logging", false)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AccountBlock(
                accounts = activeAccounts,
                onAddAccount = onAddAccount,
                onAccountClick = onAccountClick
            )

            SettingsGroup(title = "Appearance", icon = Icons.Default.DarkMode) {
                var themeMode by remember { mutableStateOf(PreferencesManager.getInstance().getString("theme_mode", "system")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System" to "system", "Light" to "light", "Dark" to "dark").forEach { (label, value) ->
                        androidx.compose.material3.FilterChip(
                            selected = themeMode == value,
                            onClick = {
                                themeMode = value
                                PreferencesManager.getInstance().putThemeMode(value)
                            },
                            label = { androidx.compose.material3.Text(label) },
                            leadingIcon = when (value) {
                                "light" -> { { androidx.compose.material3.Icon(Icons.Default.LightMode, null, Modifier.size(16.dp)) } }
                                "dark" -> { { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.DarkMode, null, Modifier.size(16.dp)) } }
                                else -> { { androidx.compose.material3.Icon(Icons.Default.BrightnessMedium, null, Modifier.size(16.dp)) } }
                            }
                        )
                    }
                }
                HorizontalDivider()
                SettingItem(title = "App Language", subtitle = "English (US)", icon = Icons.Default.Language, onClick = { /* TODO: locale picker */ })
            }

            SettingsGroup(title = "Sync", icon = Icons.Default.Sync) {
                var autoSync by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("auto_sync", true)) }
                val syncIntervalMinutes = PreferencesManager.getInstance().getSyncIntervalMinutes(15)
                val syncLabel = when (syncIntervalMinutes) {
                    30 -> "Every 30 minutes"
                    60 -> "Every 1 hour"
                    180 -> "Every 3 hours"
                    360 -> "Every 6 hours"
                    720 -> "Every 12 hours"
                    -1 -> "Manual only"
                    else -> "Every 15 minutes"
                }
                SettingItem(
                    title = "Auto-sync",
                    subtitle = if (autoSync) syncLabel else "Off",
                    icon = Icons.Default.Sync,
                    trailing = {
                        Switch(
                            checked = autoSync,
                            onCheckedChange = {
                                autoSync = it
                                PreferencesManager.getInstance().putBoolean("auto_sync", it)
                            }
                        )
                    },
                    onClick = { }
                )
                HorizontalDivider()
                var wifiOnly by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("sync_wifi_only", false)) }
                SettingItem(
                    title = "Sync on Wi-Fi only",
                    subtitle = "Save mobile data",
                    icon = Icons.Default.Wifi,
                    trailing = { Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; PreferencesManager.getInstance().putBoolean("sync_wifi_only", it) }) },
                    onClick = { }
                )
                HorizontalDivider()
                SettingItem(
                    title = "Sync interval",
                    subtitle = syncLabel,
                    icon = Icons.Default.Alarm,
                    onClick = { showReminderTime = true }
                )
            }

            SettingsGroup(title = "Notifications", icon = Icons.Default.Notifications) {
                var emailNotif by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("notif_email", true)) }
                SettingItem(title = "Email notifications", icon = Icons.Default.Email, trailing = { Switch(checked = emailNotif, onCheckedChange = { emailNotif = it; PreferencesManager.getInstance().putBoolean("notif_email", it) }) }, onClick = { })
                HorizontalDivider()
                var calRemind by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("notif_calendar", true)) }
                SettingItem(title = "Calendar reminders", icon = Icons.Default.CalendarMonth, trailing = { Switch(checked = calRemind, onCheckedChange = { calRemind = it; PreferencesManager.getInstance().putBoolean("notif_calendar", it) }) }, onClick = { })
                HorizontalDivider()
                var taskRemind by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("notif_tasks", true)) }
                SettingItem(title = "Task reminders", icon = Icons.Default.Checklist, trailing = { Switch(checked = taskRemind, onCheckedChange = { taskRemind = it; PreferencesManager.getInstance().putBoolean("notif_tasks", it) }) }, onClick = { })
                HorizontalDivider()
                var msgNotif by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("notif_messages", true)) }
                SettingItem(title = "Message notifications", icon = Icons.Default.Email, trailing = { Switch(checked = msgNotif, onCheckedChange = { msgNotif = it; PreferencesManager.getInstance().putBoolean("notif_messages", it) }) }, onClick = { })
                HorizontalDivider()
                var fullscreen by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("notif_fullscreen", true)) }
                SettingItem(title = "Full-screen reminders", icon = Icons.Default.Fullscreen, trailing = { Switch(checked = fullscreen, onCheckedChange = { fullscreen = it; PreferencesManager.getInstance().putBoolean("notif_fullscreen", it) }) }, onClick = { })
            }

            SettingsGroup(title = "Security", icon = Icons.Default.Lock) {
                var biometric by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("biometric_lock", false)) }
                SettingItem(title = "Biometric Lock", subtitle = "Require biometrics", icon = Icons.Default.Lock, trailing = { Switch(checked = biometric, onCheckedChange = { biometric = it; PreferencesManager.getInstance().putBoolean("biometric_lock", it) }) }, onClick = { })
                HorizontalDivider()
                SettingItem(title = "Encryption", icon = Icons.Default.Security, onClick = onEncryptionClick)
                HorizontalDivider()
                var noTelemetry by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("no_telemetry", false)) }
                SettingItem(title = "No Telemetry", icon = Icons.Default.Block, trailing = { Switch(checked = noTelemetry, onCheckedChange = { noTelemetry = it; PreferencesManager.getInstance().putBoolean("no_telemetry", it) }) }, onClick = { })
            }

            SettingsGroup(title = "Advanced", icon = Icons.Default.Alarm) {
                SettingItem(title = "Default reminder time", subtitle = "1 hour before events", icon = Icons.Default.Alarm, onClick = { showReminderTime = true })
                HorizontalDivider()
                SettingItem(title = "Debug logging", subtitle = "Enable for troubleshooting", icon = Icons.Default.BugReport, trailing = { Switch(checked = debugLog, onCheckedChange = { debugLog = it; PreferencesManager.getInstance().putBoolean("debug_logging", it) }) }, onClick = { })
                HorizontalDivider()
                SettingItem(title = "About", subtitle = "Version 1.0.0", icon = Icons.Default.Info, onClick = { showAbout = true })
                HorizontalDivider()
                SettingItem(title = "Clear All Data", icon = Icons.Default.Delete, textColor = Color.Red, onClick = { showClearDataConfirm = true })
            }
        }
    }

    if (showAbout) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About UnifiedComms") },
            text = { Text("Version 1.0.0 (v0.1.2)\nBuilt by Dvalin21\nF-Droid / Frisky distribution") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } }
        )
    }

    if (showReminderTime) {
        val syncIntervals = listOf(
            15 to "Every 15 minutes",
            30 to "Every 30 minutes",
            60 to "Every 1 hour",
            180 to "Every 3 hours",
            360 to "Every 6 hours",
            720 to "Every 12 hours",
            -1 to "Manual only"
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReminderTime = false },
            title = { Text("Sync Interval") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    syncIntervals.forEach { (minutes, label) ->
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    PreferencesManager.getInstance().putSyncIntervalMinutes(minutes)
                                    showReminderTime = false
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReminderTime = false }) { Text("Cancel") } }
        )
    }

    if (showClearDataConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Clear All Data") },
            text = { Text("This will delete all local data. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDataConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDataConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AccountBlock(
    accounts: List<Account>,
    onAddAccount: () -> Unit,
    onAccountClick: (Account) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Accounts", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            accounts.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clickable { onAccountClick(account) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = account.name, fontWeight = FontWeight.Bold)
                        Text(text = account.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
            TextButton(onClick = onAddAccount) {
                Text("Add Account")
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String = "",
    icon: ImageVector,
    onClick: () -> Unit = { },
    trailing: @Composable () -> Unit = {},
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = textColor, fontSize = 16.sp)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        trailing()
    }
}
