package com.unifiedcomms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.datetime.Instant
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.ui.theme.AccountColors
import com.unifiedcomms.ui.theme.UnifiedCommsTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedInboxScreen(
    viewModel: MainViewModel,
    onNavigateToEmail: (String, String) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToConversation: (String) -> Unit = {},
    onNavigateToComposeMessage: () -> Unit = {},
    onEventClick: (String) -> Unit = {},
    onCreateEvent: () -> Unit = {},
    onNavigateToContact: (String) -> Unit = {},
    onNavigateToContactNew: () -> Unit = {},
    onNavigateToTask: (String) -> Unit = {},
    onCreateTask: () -> Unit = {},
    initialTab: Int? = null
) {
    val accounts = viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccounts = accounts.value.filter { it.isActive }.distinctBy { it.id }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab?.coerceIn(0, 4) ?: 0) }
    val pendingTab by viewModel.pendingTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTab) {
        pendingTab?.let {
            selectedTab = it.coerceIn(0, 4)
            viewModel.clearPendingTab()
        }
    }

    // ponytail: email (and other legs) had NO auto-sync trigger on launch — the inbox
    // stayed empty until the user tapped the Sync icon. Calendar had a LaunchedEffect
    // trigger; email did not. Kick a foreground sync whenever the active-account set
    // appears or changes, so the inbox populates automatically like the calendar does.
    LaunchedEffect(activeAccounts.map { it.id }) {
        if (activeAccounts.isNotEmpty()) {
            coroutineScope.launch { viewModel.syncAllAccounts() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UnifiedComms", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { /* Open drawer */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            viewModel.syncAllAccounts()
                        }
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync All")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 18.dp)
            ) {
                NavigationBar(
                    tonalElevation = 6.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                ) {
                    val items = listOf(
                        NavigationItem("Inbox", Icons.Default.Inbox, 0),
                        NavigationItem("Calendar", Icons.Default.CalendarMonth, 1),
                        NavigationItem("Tasks", Icons.Default.Checklist, 2),
                        NavigationItem("Chat", Icons.AutoMirrored.Default.Message, 3),
                        NavigationItem("People", Icons.Default.Contacts, 4)
                    )
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = selectedTab == item.index,
                            onClick = { selectedTab = item.index },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> EmailOverviewScreen(activeAccounts, viewModel, onNavigateToEmail, onNavigateToAddAccount)
                1 -> CalendarScreen(viewModel, onCreateEvent = onCreateEvent, onEventClick = onEventClick)
                2 -> TasksScreen(viewModel, onCreateTask = onCreateTask, onTaskClick = { onNavigateToTask(it.id) })
                3 -> MessagesScreen(viewModel, onConversationClick = onNavigateToConversation, onNewMessage = onNavigateToComposeMessage)
                4 -> ContactsScreen(viewModel, onContactClick = onNavigateToContact, onAddContact = onNavigateToContactNew)
            }
        }
    }
}

data class NavigationItem(val label: String, val icon: ImageVector, val index: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailOverviewScreen(
    accounts: List<Account>,
    viewModel: MainViewModel,
    onNavigateToEmail: (String, String) -> Unit,
    onAddAccount: () -> Unit = {}
) {
    val activeAccounts = accounts.filter { it.isActive }
    val activeIds = activeAccounts.map { it.id }
    val repo = viewModel.emailRepository
    val emails by (if (activeIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
    else repo.getUnifiedInbox(activeIds, listOf("INBOX"), 100))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val accountColorById = remember(activeAccounts) {
        activeAccounts.associate { it.id to AccountColors.getColorForAccount(it.id) }
    }

    if (emails.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "No emails yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add an account to see your inbox.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAddAccount) {
                androidx.compose.material3.Text("Add Account")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(emails) { email ->
            val color = accountColorById[email.accountId]
                ?: com.unifiedcomms.ui.theme.AccountColor(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, "Default")
            val fromName = email.sender.name ?: email.sender.email
            val avatarColor = color.container
            val initials = email.sender.getInitials()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToEmail(email.accountId, "INBOX") },
                color = if (email.isUnread()) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.surface,
                tonalElevation = if (email.isUnread()) 1.dp else 0.dp
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).background(avatarColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = initials, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fromName,
                                fontWeight = if (email.isUnread()) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a").format(
                                    java.time.LocalDateTime.ofInstant(
                                        java.time.Instant.ofEpochMilli(email.receivedAt.toEpochMilliseconds()),
                                        java.time.ZoneId.systemDefault()
                                    )
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = email.subject.ifBlank { "(no subject)" },
                            fontWeight = if (email.isUnread()) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = email.getSnippet(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderChip(label: String, count: Int, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .clickable(onClick = { android.util.Log.e("CHIP", "chip clicked: $label"); onClick() })
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1, softWrap = false)
            if (count > 0) {
                Text(text = count.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
            }
        }
    }
}
