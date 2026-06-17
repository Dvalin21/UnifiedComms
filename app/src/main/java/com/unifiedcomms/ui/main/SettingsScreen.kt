package com.unifiedcomms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.icons.filled.ArrowBack
import androidx.compose.material3.icons.filled.Email
import androidx.compose.material3.icons.filled.Fingerprint
import androidx.compose.material3.icons.filled.Lock
import androidx.compose.material3.icons.filled.Notifications
import androidx.compose.material3.icons.filled.Palette
import androidx.compose.material3.icons.filled.Person
import androidx.compose.material3.icons.filled.Sync
import androidx.compose.material3.icons.filled.Backup
import androidx.compose.material3.icons.filled.Delete
import androidx.compose.material3.icons.filled.Info
import androidx.compose.material3.icons.filled.Language
import androidx.compose.material3.icons.filled.DarkMode
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
import androidx.compose.material3.Switch
import com.unifiedcomms.data.model.Account

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onAddAccount: () -> Unit,
    onAccountClick: (Account) -> Unit
) {
    val accounts = viewModel.accounts.collectAsStateWithLifecycle().value ?: emptyList()
    val activeAccounts = accounts.filter { it.isActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { /* Back */ }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { innerPadding ->
        androidx.compose.material3.ScrollableColumn(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Accounts section
            SettingsSection(title = "Accounts", icon = Icons.Default.Person) {
                activeAccounts.forEach { account ->
                    AccountSettingsRow(account = account, onClick = { onAccountClick(it) })
                    Divider()
                }
                androidx.compose.material3.Button(onClick = onAddAccount) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Text("Add Account")
                }
            }

            // Appearance
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                SettingRow(
                    title = "Theme",
                    subtitle = "System default",
                    icon = Icons.Default.DarkMode,
                    onClick = { /* Theme dialog */ }
                )
                Divider()
                SettingRow(
                    title = "App Language",
                    subtitle = "English (US)",
                    icon = Icons.Default.Language,
                    onClick = { /* Language dialog */ }
                )
            }

            // Sync
            SettingsSection(title = "Sync & Data", icon = Icons.Default.Sync) {
                SettingRow(
                    title = "Auto-sync",
                    subtitle = "Every 15 minutes",
                    icon = Icons.Default.Sync,
                    onClick = { /* Sync settings */ },
                    trailing = {
                        Switch(
                            checked = true,
                            onCheckedChange = { /* Toggle */ }
                        )
                    }
                )
                Divider()
                SettingRow(
                    title = "Sync on Wi-Fi only",
                    subtitle = "Save mobile data",
                    icon = Icons.Default.Wifi,
                    onClick = { },
                    trailing = {
                        Switch(
                            checked = false,
                            onCheckedChange = { }
                        )
                    }
                )
                Divider()
                SettingRow(
                    title = "Sync attachments",
                    subtitle = "Download attachments automatically",
                    icon = Icons.Default.AttachFile,
                    onClick = { },
                    trailing = {
                        Switch(
                            checked = true,
                            onCheckedChange = { }
                        )
                    }
                )
                Divider()
                SettingRow(
                    title = "Backup & Restore",
                    subtitle = "Export/import settings and data",
                    icon = Icons.Default.Backup,
                    onClick = { /* Backup dialog */ }
                )
            }

            // Notifications
            SettingsSection(title = "Notifications", icon = Icons.Default.Notifications) {
                SettingRow(
                    title = "Email notifications",
                    subtitle = "New mail alerts",
                    icon = Icons.Default.Email,
                    trailing = {
                        Switch(checked = true, onCheckedChange = { })
                    }
                )
                Divider()
                SettingRow(
                    title = "Calendar reminders",
                    subtitle = "Event notifications",
                    icon = Icons.Default.CalendarMonth,
                    trailing = {
                        Switch(checked = true, onCheckedChange = { })
                    }
                )
                Divider()
                SettingRow(
                    title = "Task reminders",
                    subtitle = "Due date alerts",
                    icon = Icons.Default.Checklist,
                    trailing = {
                        Switch(checked = true, onCheckedChange = { })
                    }
                )
                Divider()
                SettingRow(
                    title = "Message notifications",
                    subtitle = "New message alerts",
                    icon = Icons.Default.Message,
                    trailing = {
                        Switch(checked = true, onCheckedChange = { })
                    }
                )
                Divider()
                SettingRow(
                    title = "Full-screen reminders",
                    subtitle = "Show full-screen for calendar events",
                    icon = Icons.Default.Fullscreen,
                    trailing = {
                        Switch(checked = true, onCheckedChange = { })
                    }
                )
            }

            // Security & Privacy
            SettingsSection(title = "Security & Privacy", icon = Icons.Default.Lock) {
                SettingRow(
                    title = "Biometric Lock",
                    subtitle = "Require fingerprint/face to open app",
                    icon = Icons.Default.Fingerprint,
                    trailing = {
                        Switch(checked = true, onCheckedChange = { })
                    }
                )
                Divider()
                SettingRow(
                    title = "Auto-lock timeout",
                    subtitle = "5 minutes",
                    icon = Icons.Default.Timer,
                    onClick = { /* Timeout dialog */ }
                )
                Divider()
                SettingRow(
                    title = "Encryption",
                    subtitle = "All data encrypted at rest (AES-256)",
                    icon = Icons.Default.Security,
                    onClick = { /* Encryption info */ }
                )
                Divider()
                SettingRow(
                    title = "No Telemetry",
                    subtitle = "Zero tracking, zero analytics",
                    icon = Icons.Default.Block,
                    onClick = { /* Privacy info */ }
                )
            }

            // Advanced
            SettingsSection(title = "Advanced", icon = Icons.Default.Settings) {
                SettingRow(
                    title = "Default reminder time",
                    subtitle = "1 hour before events",
                    icon = Icons.Default.Alarm,
                    onClick = { /* Reminder dialog */ }
                )
                Divider()
                SettingRow(
                    title = "Debug logging",
                    subtitle = "Enable for troubleshooting",
                    icon = Icons.Default.BugReport,
                    trailing = {
                        Switch(checked = false, onCheckedChange = { })
                    }
                )
                Divider()
                SettingRow(
                    title = "About",
                    subtitle = "Version 1.0.0",
                    icon = Icons.Default.Info,
                    onClick = { /* About dialog */ }
                )
                Divider()
                SettingRow(
                    title = "Clear All Data",
                    subtitle = "Permanently delete all local data",
                    icon = Icons.Default.Delete,
                    textColor = Color.Red,
                    onClick = { /* Confirm dialog */ }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: androidx.compose.material3.icons.filled.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Divider()
            content()
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.material3.icons.filled.ImageVector,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = { Spacer(modifier = Modifier.width(0.dp)) },
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = textColor, fontSize = 16.sp)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        trailing()
    }
}

@Composable
fun AccountSettingsRow(
    account: Account,
    onClick: () -> Unit
) {
    val color = com.unifiedcomms.ui.theme.AccountColors.getColorForAccount(account.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .width(12.dp)
                .fillMaxHeight()
                .background(Color(color.container), RoundedCornerShape(6.dp))
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(text = account.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (account.isDefault) {
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Badge(
                        badgeContent = { Text("Default", fontSize = 10.sp) }
                    ) { Spacer(modifier = Modifier.size(0.dp)) }
                }
            }
            Text(text = account.email, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "Sync: ${if (account.syncConfig.syncEmail) "📧 " else ""}${if (account.syncConfig.syncCalendar) "📅 " else ""}${if (account.syncConfig.syncTasks) "✅ " else ""}${if (account.syncConfig.syncContacts) "👥 " else ""}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(Icons.Default.ArrowForwardIos, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AddAccountScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    var accountType by remember { mutableStateOf(AccountSetupType.GOOGLE) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Account", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onComplete) { Icon(Icons.Default.ArrowBack, contentDescription = "Cancel") } }
            )
        }
    ) { innerPadding ->
        androidx.compose.material3.ScrollableColumn(
            modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(text = "Choose Account Type", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountSetupType.values().forEach { type ->
                    AccountTypeCard(
                        type = type,
                        isSelected = accountType == type,
                        onClick = { accountType = type }
                    )
                }
            }

            if (accountType != AccountSetupType.GOOGLE && accountType != AccountSetupType.OUTLOOK && accountType != AccountSetupType.YAHOO) {
                Divider()
                Text(text = "Server Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                androidx.compose.material3.TextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL (e.g., mail.example.com)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Divider()
            Text(text = "Account Details", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            androidx.compose.material3.TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display Name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            androidx.compose.material3.TextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            )

            if (accountType.requiresPassword) {
                androidx.compose.material3.TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password / App Password *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.material3.PasswordVisualTransformation()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            androidx.compose.material3.Button(
                onClick = {
                    isLoading = true
                    // Create account
                    val account = when (accountType) {
                        AccountSetupType.GOOGLE -> com.unifiedcomms.data.model.Account.createGoogle(email, name)
                        AccountSetupType.MAILCOW -> com.unifiedcomms.data.model.Account.createMailcow(email, serverUrl, name)
                        AccountSetupType.OUTLOOK -> com.unifiedcomms.data.model.Account(
                            name = name.ifBlank { "Outlook ($email)" },
                            email = email,
                            accountType = com.unifiedcomms.data.model.AccountType.OUTLOOK,
                            serverConfig = com.unifiedcomms.data.model.ServerConfig.OutlookDefaults(),
                            authConfig = com.unifiedcomms.data.model.AuthConfig.OAuth2(),
                            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
                            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
                        )
                        AccountSetupType.YAHOO -> com.unifiedcomms.data.model.Account(
                            name = name.ifBlank { "Yahoo ($email)" },
                            email = email,
                            accountType = com.unifiedcomms.data.model.AccountType.YAHOO,
                            serverConfig = com.unifiedcomms.data.model.ServerConfig.YahooDefaults(),
                            authConfig = com.unifiedcomms.data.model.AuthConfig.OAuth2(),
                            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
                            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
                        )
                        AccountSetupType.EXCHANGE -> com.unifiedcomms.data.model.Account.createExchange(email, serverUrl, name)
                        AccountSetupType.ICLOUD -> com.unifiedcomms.data.model.Account(
                            name = name.ifBlank { "iCloud ($email)" },
                            email = email,
                            accountType = com.unifiedcomms.data.model.AccountType.ICLOUD,
                            serverConfig = com.unifiedcomms.data.model.ServerConfig.ICantDefaults(),
                            authConfig = com.unifiedcomms.data.model.AuthConfig.OAuth2(),
                            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
                            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
                        )
                        else -> com.unifiedcomms.data.model.Account.createGeneric(
                            email = email,
                            serverConfig = com.unifiedcomms.data.model.ServerConfig(
                                imapHost = serverUrl,
                                smtpHost = serverUrl,
                                caldavUrl = "$serverUrl/dav/",
                                carddavUrl = "$serverUrl/dav/"
                            ),
                            name = name
                        )
                    }
                    viewModel.addAccount(account)
                    isLoading = false
                    onComplete()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotBlank() && !isLoading && (accountType.requiresPassword.not() || password.isNotBlank())
            ) {
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
                }
                Text(if (isLoading) "Adding..." else "Add Account")
            }
        }
    }
}

enum class AccountSetupType(val requiresPassword: Boolean) {
    GOOGLE(false),
    MAILCOW(true),
    OUTLOOK(false),
    YAHOO(false),
    EXCHANGE(false),
    ICLOUD(false),
    GENERIC_IMAP(true),
    GENERIC_CALDAV(true)
}

@Composable
fun AccountTypeCard(
    type: AccountSetupType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (title, subtitle, icon) = when (type) {
        AccountSetupType.GOOGLE -> "Google" to "Gmail, Calendar, Contacts, Tasks" to Icons.Default.Email
        AccountSetupType.MAILCOW -> "Mailcow" to "Self-hosted email (SOGo)" to Icons.Default.Email
        AccountSetupType.OUTLOOK -> "Outlook / Hotmail" to "Outlook.com, Office 365" to Icons.Default.Email
        AccountSetupType.YAHOO -> "Yahoo Mail" to "Yahoo Calendar & Contacts" to Icons.Default.Email
        AccountSetupType.EXCHANGE -> "Exchange / Office 365" to "Corporate email & calendar" to Icons.Default.Email
        AccountSetupType.ICLOUD -> "iCloud" to "Apple Mail, Calendar, Contacts" to Icons.Default.Email
        AccountSetupType.GENERIC_IMAP -> "Generic IMAP/SMTP" to "Any email server" to Icons.Default.Email
        AccountSetupType.GENERIC_CALDAV -> "CalDAV/CardDAV" to "Any CalDAV/CardDAV server" to Icons.Default.CalendarMonth
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(12.dp)
            )
            .border(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, 2.dp, RoundedCornerShape(12.dp)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AccountSettingsScreen(
    viewModel: MainViewModel,
    accountId: String,
    onBack: () -> Unit
) {
    val account = viewModel.accounts.collectAsStateWithLifecycle().value?.find { it.id == accountId }
    val color = account?.let { com.unifiedcomms.ui.theme.AccountColors.getColorForAccount(it.id) }
        ?: com.unifiedcomms.ui.theme.AccountColor(0xFF6750A4, 0xFFFFFFFF, "Default")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.name ?: "Account Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { innerPadding ->
        androidx.compose.material3.ScrollableColumn(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(color.container)
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Box(
                            modifier = Modifier.size(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = account?.name?.first()?.uppercase() ?: "?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(color.onContainer))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = account?.name ?: "", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(color.onContainer))
                            Text(text = account?.email ?: "", fontSize = 14.sp, color = Color(color.onContainer).copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // Sync settings
            SettingsSection(title = "Sync Settings", icon = Icons.Default.Sync) {
                account?.syncConfig?.let { sync ->
                    SettingRow(title = "Sync Email", subtitle = "", icon = Icons.Default.Email, trailing = { Switch(checked = sync.syncEmail, onCheckedChange = { }) })
                    Divider()
                    SettingRow(title = "Sync Calendar", subtitle = "", icon = Icons.Default.CalendarMonth, trailing = { Switch(checked = sync.syncCalendar, onCheckedChange = { }) })
                    Divider()
                    SettingRow(title = "Sync Contacts", subtitle = "", icon = Icons.Default.Person, trailing = { Switch(checked = sync.syncContacts, onCheckedChange = { }) })
                    Divider()
                    SettingRow(title = "Sync Tasks", subtitle = "", icon = Icons.Default.Checklist, trailing = { Switch(checked = sync.syncTasks, onCheckedChange = { }) })
                    Divider()
                    SettingRow(title = "Sync Interval", subtitle = "${sync.syncIntervalMinutes} minutes", icon = Icons.Default.Timer, onClick = { })
                    Divider()
                    SettingRow(title = "Push Notifications", subtitle = "", icon = Icons.Default.Notifications, trailing = { Switch(checked = sync.pushEnabled, onCheckedChange = { }) })
                    Divider()
                    SettingRow(title = "Download Attachments", subtitle = "", icon = Icons.Default.AttachFile, trailing = { Switch(checked = sync.downloadAttachments, onCheckedChange = { }) })
                }
            }

            // UI Settings
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                SettingRow(
                    title = "Account Color",
                    subtitle = color.name,
                    icon = Icons.Default.Palette,
                    onClick = { /* Color picker */ }
                )
                Divider()
                SettingRow(
                    title = "Show in Unified Inbox",
                    subtitle = "",
                    icon = Icons.Default.Inbox,
                    trailing = { Switch(checked = account?.uiConfig?.showInUnifiedInbox ?: true, onCheckedChange = { }) }
                )
                Divider()
                SettingRow(
                    title = "Notification Priority",
                    subtitle = account?.uiConfig?.notificationPriority?.name ?: "HIGH",
                    icon = Icons.Default.Notifications,
                    onClick = { }
                )
            }

            // Account actions
            SettingsSection(title = "Account", icon = Icons.Default.Person) {
                SettingRow(
                    title = "Test Connection",
                    subtitle = "Verify server connectivity",
                    icon = Icons.Default.Wifi,
                    onClick = { /* Test */ }
                )
                Divider()
                SettingRow(
                    title = "Re-authenticate",
                    subtitle = "Update credentials",
                    icon = Icons.Default.Lock,
                    onClick = { }
                )
                Divider()
                SettingRow(
                    title = "Set as Default",
                    subtitle = "",
                    icon = Icons.Default.Star,
                    trailing = { Switch(checked = account?.isDefault ?: false, onCheckedChange = { }) }
                )
                Divider()
                SettingRow(
                    title = "Remove Account",
                    subtitle = "Delete all local data for this account",
                    icon = Icons.Default.Delete,
                    textColor = Color.Red,
                    onClick = { /* Confirm delete */ }
                )
            }
        }
    }
}