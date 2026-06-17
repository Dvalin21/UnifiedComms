package com.unifiedcomms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.AccountCircle
import androidx.compose.material3.icons.filled.Add
import androidx.compose.material3.icons.filled.CalendarMonth
import androidx.compose.material3.icons.filled.Checklist
import androidx.compose.material3.icons.filled.Email
import androidx.compose.material3.icons.filled.Inbox
import androidx.compose.material3.icons.filled.Menu
import androidx.compose.material3.icons.filled.Message
import androidx.compose.material3.icons.filled.MoreVert
import androidx.compose.material3.icons.filled.Search
import androidx.compose.material3.icons.filled.Settings
import androidx.compose.material3.icons.filled.Sync
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Medium
import androidx.navigation.compose.NavController
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.ui.theme.AccountColors
import com.unifiedcomms.ui.theme.UnifiedCommsTheme
import kotlinx.coroutines.flow.combine

@Composable
fun UnifiedInboxScreen(
    viewModel: MainViewModel,
    onNavigateToEmail: (String, String) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddAccount: () -> Unit
) {
    val accounts = viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccounts = accounts.value?.filter { it.isActive } ?: emptyList()
    val windowSize = remember { WindowSizeClass() }
    val widthSizeClass = windowSize.widthSizeClass

    val selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UnifiedComms", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { /* Open drawer */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAddAccount) {
                        Icon(Icons.Default.Add, contentDescription = "Add Account")
                    }
                    IconButton(onClick = { viewModel.syncAllAccounts() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync All")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    NavigationItem("Inbox", Icons.Default.Inbox, 0),
                    NavigationItem("Email", Icons.Default.Email, 1),
                    NavigationItem("Calendar", Icons.Default.CalendarMonth, 2),
                    NavigationItem("Tasks", Icons.Default.Checklist, 3),
                    NavigationItem("Messages", Icons.Default.Message, 4)
                )
                items.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTab == item.index,
                        onClick = { /* Handle tab selection */ },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> UnifiedInboxContent(activeAccounts, onNavigateToEmail)
                1 -> EmailOverviewScreen(activeAccounts, onNavigateToEmail)
                2 -> CalendarScreen(viewModel, onNavigateToCalendar, { })
                3 -> TasksScreen(viewModel, { }, { })
                4 -> MessagesScreen(viewModel, { }, { })
            }
        }
    }
}

data class NavigationItem(val label: String, val icon: androidx.compose.material3.icons.filled.ImageVector, val index: Int)

@Composable
fun UnifiedInboxContent(
    accounts: List<Account>,
    onNavigateToEmail: (String, String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        accounts.forEachIndexed { index, account ->
            val color = AccountColors.getColorForAccount(account.id)
            AccountInboxCard(
                account = account,
                color = color,
                onClick = { onNavigateToEmail(account.id, "INBOX") }
            )
        }
    }
}

@Composable
fun AccountInboxCard(
    account: Account,
    color: com.unifiedcomms.ui.theme.AccountColor,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(color.container),
        elevation = 2.dp,
        onClick = onClick
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Account avatar/indicator
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = account.name.first().uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(color.onContainer)
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
            // Account info
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = account.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(color.onContainer))
                Text(text = account.email, fontSize = 14.sp, color = Color(color.onContainer).copy(alpha = 0.8f))
            }
            // Unread count badge
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .wrapContentSize()
                    .background(Color(0xFFE57373), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "5", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun EmailOverviewScreen(
    accounts: List<Account>,
    onNavigateToEmail: (String, String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        accounts.forEach { account ->
            val color = AccountColors.getColorForAccount(account.id)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = account.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(color.container))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = account.email, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FolderChip("Inbox", "INBOX", 5, color.container) { onNavigateToEmail(account.id, "INBOX") }
                        FolderChip("Sent", "Sent", 0, color.container) { onNavigateToEmail(account.id, "Sent") }
                        FolderChip("Drafts", "Drafts", 2, color.container) { onNavigateToEmail(account.id, "Drafts") }
                        FolderChip("Trash", "Trash", 0, color.container) { onNavigateToEmail(account.id, "Trash") }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderChip(label: String, folder: String, count: Int, color: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.25f)
            .padding(vertical = 8.dp)
            .background(Color(color).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            if (count > 0) {
                Text(text = count.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(color))
            }
        }
    }
}