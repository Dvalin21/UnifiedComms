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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.unifiedcomms.sync.BackgroundSyncScheduler
import com.unifiedcomms.util.PreferencesManager
import android.app.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val visibleAccounts = accounts
    var showAbout by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showSyncInterval by remember { mutableStateOf(false) }
    var showDefaultReminder by remember { mutableStateOf(false) }
    var defaultReminderMinutes by remember {
        mutableStateOf(PreferencesManager.getInstance().getDefaultReminderMinutes())
    }
    var syncIntervalMinutes by remember {
        mutableStateOf(PreferencesManager.getInstance().getSyncIntervalMinutes(15))
    }
    val context = LocalContext.current
    val notificationManager = remember { context.getSystemService(android.app.NotificationManager::class.java) }
    var notificationsAllowed by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var fullScreenAllowed by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                notificationManager?.canUseFullScreenIntent() == true
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, notificationManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                fullScreenAllowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    notificationManager?.canUseFullScreenIntent() == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                accounts = visibleAccounts,
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
            }

            SettingsGroup(title = "Sync", icon = Icons.Default.Sync) {
                var autoSync by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("auto_sync", true)) }
                val syncLabel = when (syncIntervalMinutes) {
                    5 -> "Every 5 minutes"
                    15 -> "Every 15 minutes"
                    30 -> "Every 30 minutes"
                    60 -> "Every 1 hour"
                    120 -> "Every 2 hours"
                    180 -> "Every 3 hours"
                    240 -> "Every 4 hours"
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
                                BackgroundSyncScheduler.schedule(
                                    context,
                                    syncIntervalMinutes.toLong(),
                                    autoSync = it,
                                    wifiOnly = PreferencesManager.getInstance().getBoolean("sync_wifi_only", false),
                                    replaceShort = true
                                )
                            }
                        )
                    },
                )
                HorizontalDivider()
                var wifiOnly by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("sync_wifi_only", false)) }
                SettingItem(
                    title = "Sync on unmetered networks",
                    subtitle = "Avoid metered mobile data",
                    icon = Icons.Default.Wifi,
                    trailing = {
                        Switch(checked = wifiOnly, onCheckedChange = {
                            wifiOnly = it
                            PreferencesManager.getInstance().putBoolean("sync_wifi_only", it)
                            BackgroundSyncScheduler.schedule(
                                context,
                                syncIntervalMinutes.toLong(),
                                autoSync = autoSync,
                                wifiOnly = it,
                                replaceShort = true
                            )
                        })
                    },
                )
                HorizontalDivider()
                SettingItem(
                    title = "Sync interval",
                    subtitle = syncLabel,
                    icon = Icons.Default.Alarm,
                    onClick = { showSyncInterval = true }
                )
            }

            SettingsGroup(title = "Notifications", icon = Icons.Default.Notifications) {
                var emailNotif by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("notif_email", true)) }
                SettingItem(title = "Email notifications", icon = Icons.Default.Email, trailing = { Switch(checked = emailNotif, onCheckedChange = { emailNotif = it; PreferencesManager.getInstance().putBoolean("notif_email", it) }) })
                HorizontalDivider()
                var calRemind by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("notif_calendar", true)) }
                SettingItem(title = "Calendar reminders", icon = Icons.Default.CalendarMonth, trailing = {
                    Switch(checked = calRemind, onCheckedChange = {
                        calRemind = it
                        PreferencesManager.getInstance().putBoolean("notif_calendar", it)
                        if (it) viewModel.scheduleCalendarReminders() else viewModel.cancelCalendarReminders()
                    })
                })
                HorizontalDivider()
                var fullscreen by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("notif_fullscreen", true)) }
                SettingItem(
                    title = "Full-screen reminders",
                    subtitle = when {
                        !fullscreen -> "Off"
                        !fullScreenAllowed -> "Tap to grant Android permission"
                        !notificationsAllowed -> "Notification permission required"
                        else -> "Wake the screen and show the reminder"
                    },
                    icon = Icons.Default.Fullscreen,
                    trailing = { Switch(checked = fullscreen, onCheckedChange = { fullscreen = it; PreferencesManager.getInstance().putBoolean("notif_fullscreen", it) }) },
                    onClick = {
                        if (fullscreen && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !fullScreenAllowed) {
                            context.startActivity(android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                android.net.Uri.parse("package:${context.packageName}")
                            ))
                        } else if (fullscreen && !notificationsAllowed) {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            })
                        }
                    }
                )
                HorizontalDivider()
                SettingItem(
                    title = "Notification permission",
                    subtitle = if (notificationsAllowed) "Allowed" else "Required for calendar reminders",
                    icon = Icons.Default.Notifications,
                    onClick = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }
                )
            }

            SettingsGroup(title = "Security", icon = Icons.Default.Lock) {
                var biometric by remember { mutableStateOf(PreferencesManager.getInstance().getBoolean("biometric_lock", false)) }
                val activity = context as? FragmentActivity
                val executor = remember { ContextCompat.getMainExecutor(context) }
                SettingItem(title = "Biometric Lock", subtitle = "Require biometrics", icon = Icons.Default.Lock, trailing = {
                    Switch(checked = biometric, onCheckedChange = { wantOn ->
                        if (wantOn) {
                            // ponytail: when enabling, immediately verify the user via the
                            // system biometric prompt. Only commit the pref after success, so
                            // there is no separate "Unlock" step and no app restart needed.
                            val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            val canAuth = if (activity == null) BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                            else BiometricManager.from(context).canAuthenticate(allowed)
                            if (activity == null || canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                                biometric = false
                                return@Switch
                            }
                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Enable Biometric Lock")
                                .setSubtitle("Authenticate to confirm")
                                .setAllowedAuthenticators(allowed)
                                .build()
                            BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    biometric = true
                                    PreferencesManager.getInstance().putBoolean("biometric_lock", true)
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    super.onAuthenticationError(errorCode, errString)
                                    biometric = false
                                }
                                override fun onAuthenticationFailed() {
                                    super.onAuthenticationFailed()
                                    biometric = false
                                }
                            }).authenticate(promptInfo)
                        } else {
                            biometric = false
                            PreferencesManager.getInstance().putBoolean("biometric_lock", false)
                        }
                    })
                })
                HorizontalDivider()
                SettingItem(title = "Encryption", icon = Icons.Default.Security, onClick = onEncryptionClick)
            }

            SettingsGroup(title = "Advanced", icon = Icons.Default.Alarm) {
                SettingItem(
                    title = "Default reminder time",
                    subtitle = reminderLabel(defaultReminderMinutes),
                    icon = Icons.Default.Alarm,
                    onClick = { showDefaultReminder = true }
                )
                HorizontalDivider()
                SettingItem(title = "About", subtitle = "Version ${com.unifiedcomms.BuildConfig.VERSION_NAME}", icon = Icons.Default.Info, onClick = { showAbout = true })
                HorizontalDivider()
                SettingItem(title = "Clear cached data", icon = Icons.Default.Delete, textColor = Color.Red, onClick = { showClearDataConfirm = true })
            }
        }
    }

    if (showAbout) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About UnifiedComms") },
            text = { Text("Version ${com.unifiedcomms.BuildConfig.VERSION_NAME}\nBuilt by Dvalin21\nF-Droid / Frisky distribution") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } }
        )
    }

    if (showSyncInterval) {
        val syncIntervals = listOf(
            5 to "Every 5 minutes",
            15 to "Every 15 minutes",
            30 to "Every 30 minutes",
            60 to "Every 1 hour",
            120 to "Every 2 hours",
            180 to "Every 3 hours",
            240 to "Every 4 hours",
            360 to "Every 6 hours",
            720 to "Every 12 hours",
            -1 to "Manual only"
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSyncInterval = false },
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
                                    syncIntervalMinutes = minutes
                                    BackgroundSyncScheduler.schedule(
                                        context,
                                        minutes.toLong(),
                                        autoSync = PreferencesManager.getInstance().getBoolean("auto_sync", true),
                                        wifiOnly = PreferencesManager.getInstance().getBoolean("sync_wifi_only", false),
                                        replaceShort = true
                                    )
                                    showSyncInterval = false
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSyncInterval = false }) { Text("Cancel") } }
        )
    }

    if (showDefaultReminder) {
        val reminderOptions = listOf(
            0 to "At event time",
            5 to "5 minutes before",
            10 to "10 minutes before",
            15 to "15 minutes before",
            30 to "30 minutes before",
            60 to "1 hour before",
            120 to "2 hours before",
            1440 to "1 day before"
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDefaultReminder = false },
            title = { Text("Default reminder time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminderOptions.forEach { (minutes, label) ->
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    PreferencesManager.getInstance().putDefaultReminderMinutes(minutes)
                                    defaultReminderMinutes = minutes
                                    showDefaultReminder = false
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDefaultReminder = false }) { Text("Cancel") } }
        )
    }

    if (showClearDataConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Clear cached data") },
            text = { Text("This deletes local account, email, calendar, task, and contact data. Settings and downloaded files are kept. This action cannot be undone.") },
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

private fun reminderLabel(minutes: Int): String = when (minutes) {
    0 -> "At event time"
    5 -> "5 minutes before events"
    10 -> "10 minutes before events"
    15 -> "15 minutes before events"
    30 -> "30 minutes before events"
    60 -> "1 hour before events"
    120 -> "2 hours before events"
    1440 -> "1 day before events"
    else -> "$minutes minutes before events"
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
                        Text(
                            text = if (account.isActive) account.email else "${account.email} · Disabled",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
