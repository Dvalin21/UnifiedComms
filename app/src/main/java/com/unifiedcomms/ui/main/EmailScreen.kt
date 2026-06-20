package com.unifiedcomms.ui.main

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScreen(
    viewModel: MainViewModel,
    accountId: String,
    folder: String,
    onNavigateBack: () -> Unit,
    onCompose: () -> Unit
) {
    var messages by remember { mutableStateOf<List<EmailMessage>>(getMockEmails()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text("Unified Inbox", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.Search, contentDescription = "Search") }
                    IconButton(onClick = onCompose) { Icon(Icons.Default.Add, contentDescription = "Compose") }
                }
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(onClick = onCompose) {
                Icon(Icons.Default.Add, contentDescription = "Compose")
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            items(messages) { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* open */ },
                    color = if (message.isUnread) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (message.isUnread) 1.dp else 0.dp
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Text(text = message.from.first().uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Text(text = message.from, fontWeight = if (message.isUnread) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text(text = message.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Text(text = message.subject, fontWeight = if (message.isUnread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            androidx.compose.material3.Text(text = message.body, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Divider()
            }
        }
    }
}

data class EmailMessage(
    val id: String,
    val from: String,
    val subject: String,
    val body: String,
    val time: String,
    val isUnread: Boolean,
    val accountColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeEmailScreen(viewModel: MainViewModel, onSend: () -> Unit) {
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text("New Message") },
                navigationIcon = { IconButton(onClick = onSend) { Icon(Icons.Default.ArrowBack, contentDescription = "Cancel") } },
                actions = {
                    IconButton(onClick = onSend) { Icon(Icons.Default.Send, contentDescription = "Send") }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(value = to, onValueChange = { to = it }, label = { Text("To") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 8)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailScreen(viewModel: MainViewModel, emailId: String, onBack: () -> Unit) {
    val email = getMockEmails().find { it.id == emailId }
        ?: EmailMessage("1", "sender@example.com", "Subject", "Body", "10:30 AM", true, Color.Gray)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Reply, contentDescription = "Reply") }
                    IconButton(onClick = onBack) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    IconButton(onClick = onBack) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.material3.Text(text = email.subject, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(text = email.from.first().uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    androidx.compose.material3.Text(text = email.from, fontWeight = FontWeight.Bold)
                    androidx.compose.material3.Text(text = "to me", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Divider()
            androidx.compose.material3.Text(text = email.body)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeMessageScreen(
    viewModel: MainViewModel,
    conversationId: String,
    onSend: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Message") },
                navigationIcon = { IconButton(onClick = onSend) { Icon(Icons.Default.ArrowBack, contentDescription = "Cancel") } },
                actions = {
                    IconButton(onClick = onSend) { Icon(Icons.Default.Send, contentDescription = "Send") }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(value = "", onValueChange = { }, label = { Text("Recipient") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = messageText, onValueChange = { messageText = it }, label = { Text("Message") }, modifier = Modifier.fillMaxSize(), minLines = 8)
        }
    }
}

fun getMockEmails(): List<EmailMessage> = listOf(
    EmailMessage("1", "Alice Johnson", "Project Update - Q4 Goals", "Hi team, just wanted to share the latest updates on our Q4 objectives...", "10:30 AM", true, Color(0xFF6750A4)),
    EmailMessage("2", "Bob Smith", "Meeting Scheduled for Tomorrow", "Hi, I've scheduled our weekly sync for tomorrow at 2 PM...", "9:15 AM", false, Color(0xFF625B71)),
    EmailMessage("3", "Charlie Brown", "New Feature Request", "I've been thinking about a new feature that could really improve our workflow...", "Yesterday", false, Color(0xFF7D5260)),
    EmailMessage("4", "Diana Prince", "Bug Fix Deployed", "The fix for the authentication issue has been deployed to production...", "2 days ago", true, Color(0xFF9C27B0)),
    EmailMessage("5", "UnifiedComms Team", "Welcome to UnifiedComms!", "Welcome to your new unified communication platform...", "3 days ago", false, Color(0xFFB00020))
)
