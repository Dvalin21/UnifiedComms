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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.ArrowBack
import androidx.compose.material3.icons.filled.Delete
import androidx.compose.material3.icons.filled.Edit
import androidx.compose.material3.icons.filled.FileDownload
import androidx.compose.material3.icons.filled.Flag
import androidx.compose.material3.icons.filled.MarkEmailUnread
import androidx.compose.material3.icons.filled.MoreVert
import androidx.compose.material3.icons.filled.Reply
import androidx.compose.material3.icons.filled.StarBorder
import androidx.compose.material3.icons.filled.Star
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
import kotlinx.coroutines.flow.collectAsStateWithLifecycle

@Composable
fun EmailScreen(
    viewModel: MainViewModel,
    accountId: String,
    folder: String,
    onNavigateBack: () -> Unit,
    onComposeEmail: () -> Unit
) {
    val account = viewModel.accounts.collectAsStateWithLifecycle().value?.find { it.id == accountId }
    val color = account?.let { com.unifiedcomms.ui.theme.AccountColors.getColorForAccount(it.id) }
        ?: com.unifiedcomms.ui.theme.AccountColor(0xFF6750A4, 0xFFFFFFFF, "Default")

    // Mock emails for demonstration
    val emails = remember { mutableStateOf<List<MockEmail>>(getMockEmails()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(folder, fontWeight = FontWeight.Bold, color = Color(color.onContainer))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(color.onContainer))
                    }
                },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(color.onContainer))
                    }
                    IconButton(onClick = onComposeEmail) {
                        Icon(Icons.Default.Edit, contentDescription = "Compose", tint = Color(color.onContainer))
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(color.container)
                )
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = onComposeEmail,
                containerColor = Color(color.container),
                contentColor = Color(color.onContainer)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Compose")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            items(emails.value) { email ->
                EmailListItem(
                    email = email,
                    accountColor = color,
                    onClick = { /* Open email detail */ },
                    onStarToggle = { emails.value = emails.value.map { if (it.id == email.id) it.copy(isStarred = !it.isStarred) else it } },
                    onReadToggle = { emails.value = emails.value.map { if (it.id == email.id) it.copy(isRead = !it.isRead) else it } },
                    onDelete = { emails.value = emails.value.filter { it.id != email.id } }
                )
                Divider()
            }
        }
    }
}

data class MockEmail(
    val id: String,
    val sender: String,
    val senderEmail: String,
    val subject: String,
    val preview: String,
    val time: String,
    val isRead: Boolean,
    val isStarred: Boolean,
    val hasAttachments: Boolean,
    val labels: List<String>
)

fun getMockEmails(): List<MockEmail> = listOf(
    MockEmail("1", "John Doe", "john@example.com", "Meeting tomorrow at 10am", "Hi team, just a reminder about our meeting...", "10:30 AM", false, false, true, listOf("Work")),
    MockEmail("2", "Jane Smith", "jane@company.com", "Project update - Q4 results", "The quarterly results are in and looking great...", "9:15 AM", false, true, false, listOf("Important")),
    MockEmail("3", "GitHub", "notifications@github.com", "New pull request in unifiedcomms", "Pull request #42 opened by contributor...", "8:00 AM", true, false, false, listOf("Notifications")),
    MockEmail("4", "Amazon", "orders@amazon.com", "Your order has shipped", "Your package is on the way with tracking...", "Yesterday", true, false, false, listOf("Promotions")),
    MockEmail("5", "Security Team", "security@bank.com", "Important: Password change required", "For your security, please update your password...", "2 days ago", false, false, false, listOf("Security"))
)

@Composable
fun EmailListItem(
    email: MockEmail,
    accountColor: com.unifiedcomms.ui.theme.AccountColor,
    onClick: () -> Unit,
    onStarToggle: () -> Unit,
    onReadToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(56.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = if (!email.isRead) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
        elevation = if (!email.isRead) 2.dp else 0.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Star
            IconButton(onClick = onStarToggle) {
                Icon(
                    imageVector = if (email.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Star",
                    tint = if (email.isStarred) Color(accountColor.container) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Sender and subject
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = email.sender,
                        fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                    )
                    if (email.hasAttachments) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.FileDownload, contentDescription = "Attachment", tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = email.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = email.subject,
                        fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = email.preview, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.overflow.TextOverflow.Ellipsis)
                }
            }

            // Labels
            email.labels.forEach { label ->
                Surface(
                    modifier = Modifier.padding(start = 8.dp).padding(vertical = 4.dp, horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    containerColor = Color(accountColor.container).copy(alpha = 0.15f)
                ) {
                    Text(text = label, fontSize = 10.sp, color = Color(accountColor.container), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun ComposeEmailScreen(
    viewModel: MainViewModel,
    accountId: String,
    onSend: () -> Unit
) {
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose Email") },
                navigationIcon = {
                    IconButton(onClick = onSend) { Icon(Icons.Default.ArrowBack, contentDescription = "Cancel") }
                },
                actions = {
                    IconButton(onClick = { /* Send email */ onSend() }) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // To field
            androidx.compose.material3.TextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("To") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Subject field
            androidx.compose.material3.TextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Body field
            androidx.compose.material3.TextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 10,
                maxLines = 20
            )
        }
    }
}