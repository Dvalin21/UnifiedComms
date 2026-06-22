package com.unifiedcomms.ui.main
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    onAccountClick: (Account) -> Unit
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccounts = accounts.filter { it.isActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { /* TODO: back */ }) {
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AccountBlock(
                accounts = activeAccounts,
                onAddAccount = onAddAccount,
                onAccountClick = onAccountClick
            )

            SettingsGroup(title = "Appearance", icon = Icons.Default.DarkMode) {
                SettingItem(title = "Theme", subtitle = "System default", icon = Icons.Default.DarkMode, onClick = { })
                HorizontalDivider()
                SettingItem(title = "App Language", subtitle = "English (US)", icon = Icons.Default.Language, onClick = { })
            }

            SettingsGroup(title = "Sync", icon = Icons.Default.Sync) {
                var autoSync by remember { mutableStateOf(true) }
                SettingItem(
                    title = "Auto-sync",
                    subtitle = "Every 15 minutes",
                    icon = Icons.Default.Sync,
                    trailing = { Switch(checked = autoSync, onCheckedChange = { autoSync = it }) },
                    onClick = { }
                )
                HorizontalDivider()
                SettingItem(title = "Sync on Wi-Fi only", subtitle = "Save mobile data", icon = Icons.Default.Wifi, onClick = { })
            }

            SettingsGroup(title = "Notifications", icon = Icons.Default.Notifications) {
                SettingItem(title = "Email notifications", icon = Icons.Default.Email, trailing = { Switch(checked = true, onCheckedChange = { }) }, onClick = { })
                HorizontalDivider()
                SettingItem(title = "Calendar reminders", icon = Icons.Default.CalendarMonth, trailing = { Switch(checked = true, onCheckedChange = { }) }, onClick = { })
                HorizontalDivider()
                SettingItem(title = "Task reminders", icon = Icons.Default.Checklist, trailing = { Switch(checked = true, onCheckedChange = { }) }, onClick = { })
                HorizontalDivider()
                SettingItem(title = "Message notifications", icon = Icons.Default.Email, trailing = { Switch(checked = true, onCheckedChange = { }) }, onClick = { })
                HorizontalDivider()
                SettingItem(title = "Full-screen reminders", icon = Icons.Default.Fullscreen, trailing = { Switch(checked = true, onCheckedChange = { }) }, onClick = { })
            }

            SettingsGroup(title = "Security", icon = Icons.Default.Lock) {
                SettingItem(title = "Biometric Lock", subtitle = "Require biometrics", icon = Icons.Default.Lock, trailing = { Switch(checked = true, onCheckedChange = { }) }, onClick = { })
                HorizontalDivider()
                SettingItem(title = "Encryption", icon = Icons.Default.Security, onClick = { })
                HorizontalDivider()
                SettingItem(title = "No Telemetry", icon = Icons.Default.Block, onClick = { })
            }

            SettingsGroup(title = "Advanced", icon = Icons.Default.Alarm) {
                SettingItem(title = "Default reminder time", subtitle = "1 hour before events", icon = Icons.Default.Alarm, onClick = { })
                HorizontalDivider()
                SettingItem(title = "Debug logging", subtitle = "Enable for troubleshooting", icon = Icons.Default.BugReport, trailing = { Switch(checked = false, onCheckedChange = { }) }, onClick = { })
                HorizontalDivider()
                SettingItem(title = "About", subtitle = "Version 1.0.0", icon = Icons.Default.Info, onClick = { })
                HorizontalDivider()
                SettingItem(title = "Clear All Data", icon = Icons.Default.Delete, textColor = Color.Red, onClick = { })
            }
        }
    }
}

@Composable
private fun AccountBlock(
    accounts: List<Account>,
    onAddAccount: () -> Unit,
    onAccountClick: (Account) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp))
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
