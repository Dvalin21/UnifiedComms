package com.unifiedcomms.ui.main
import androidx.compose.material.icons.Icons
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.ui.theme.AccountColor
import com.unifiedcomms.ui.theme.AccountColors
import com.unifiedcomms.ui.theme.UnifiedCommsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AccountSettingsScreen(
    viewModel: MainViewModel,
    accountId: String,
    onBack: () -> Unit,
    coroutineScope: CoroutineScope? = null
) {
    // ponytail: read the account from the collected accounts stream, not a direct call during
    // composition — otherwise a not-yet-loaded list shows "Account not found" forever and the
    // lookup re-runs every recomposition.
    val allAccounts by viewModel.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val account = allAccounts.find { it.id == accountId }
    if (account == null) {
        UnifiedCommsTheme {
            Scaffold { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                    Text("Account not found")
                }
            }
        }
        return
    }

    val color: AccountColor = viewModel.getAccountColor(account.id)
    val effectiveScope = coroutineScope ?: rememberCoroutineScope()
    var accountState by remember { mutableStateOf(account) }
    var syncEmail by remember { mutableStateOf(account.syncConfig.syncEmail) }
    var syncCalendar by remember { mutableStateOf(account.syncConfig.syncCalendar) }
    var syncTasks by remember { mutableStateOf(account.syncConfig.syncTasks) }
    var syncContacts by remember { mutableStateOf(account.syncConfig.syncContacts) }
    var isActive by remember { mutableStateOf(account.isActive) }
    var isDefault by remember { mutableStateOf(account.isDefault) }

    UnifiedCommsTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Account Settings") },
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = color.container,
                        tonalElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = accountState.name.firstOrNull()?.uppercase() ?: "?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = color.onContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = accountState.name.ifBlank { accountState.email.ifBlank { "Account" } },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = accountState.email,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                SettingItem(
                    title = "Email sync",
                    subtitle = "Sync email",
                    icon = Icons.Default.Email,
                    trailing = {
                        Switch(checked = syncEmail, onCheckedChange = {
                            syncEmail = it
                            effectiveScope.launch {
                                accountState = viewModel.updateAccount(accountState.copy(syncConfig = accountState.syncConfig.copy(syncEmail = it)))
                            }
                        })
                    }
                )
                HorizontalDivider()
                SettingItem(
                    title = "Calendar sync",
                    subtitle = "Sync calendar",
                    icon = Icons.Default.CalendarMonth,
                    trailing = {
                        Switch(checked = syncCalendar, onCheckedChange = {
                            syncCalendar = it
                            effectiveScope.launch {
                                accountState = viewModel.updateAccount(accountState.copy(syncConfig = accountState.syncConfig.copy(syncCalendar = it)))
                            }
                        })
                    }
                )
                HorizontalDivider()
                SettingItem(
                    title = "Tasks sync",
                    subtitle = "Sync tasks",
                    icon = Icons.Default.Checklist,
                    trailing = {
                        Switch(checked = syncTasks, onCheckedChange = {
                            syncTasks = it
                            effectiveScope.launch {
                                accountState = viewModel.updateAccount(accountState.copy(syncConfig = accountState.syncConfig.copy(syncTasks = it)))
                            }
                        })
                    }
                )
                HorizontalDivider()
                SettingItem(
                    title = "Contacts sync",
                    subtitle = "Sync contacts",
                    icon = Icons.Default.AccountCircle,
                    trailing = {
                        Switch(checked = syncContacts, onCheckedChange = {
                            syncContacts = it
                            effectiveScope.launch {
                                accountState = viewModel.updateAccount(accountState.copy(syncConfig = accountState.syncConfig.copy(syncContacts = it)))
                            }
                        })
                    }
                )

                HorizontalDivider()

                SettingItem(
                    title = "Default account",
                    subtitle = if (isDefault) "Yes" else "No",
                    icon = Icons.Default.Lock,
                    trailing = {
                        Switch(
                            checked = isDefault,
                            onCheckedChange = {
                                isDefault = it
                                effectiveScope.launch {
                                    viewModel.setDefaultAccount(accountState.id)
                                }
                            }
                        )
                    }
                )
                HorizontalDivider()
                SettingItem(
                    title = "Active",
                    subtitle = if (isActive) "Enabled" else "Disabled",
                    icon = Icons.Default.Lock,
                    trailing = {
                        Switch(
                            checked = isActive,
                            onCheckedChange = {
                                isActive = it
                                effectiveScope.launch {
                                    accountState = viewModel.updateAccount(accountState.copy(isActive = it))
                                }
                            }
                        )
                    }
                )
                HorizontalDivider()
                Text(
                    text = "Remove account",
                    color = Color.Red,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            effectiveScope.launch {
                                viewModel.removeAccount(accountState.id)
                                onBack()
                            }
                        }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}