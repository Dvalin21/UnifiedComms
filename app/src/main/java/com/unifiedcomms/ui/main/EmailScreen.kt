package com.unifiedcomms.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.EmailAddress
import com.unifiedcomms.data.model.EmailRecipients
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScreen(
    viewModel: MainViewModel,
    accountId: String,
    folder: String,
    onNavigateBack: () -> Unit,
    onCompose: () -> Unit
) {
    val emails by viewModel.emailRepository
        .getByAccountAndFolder(accountId, folder, 100, 0)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val messages = emails.map { it.toEmailMessage() }

    var deleteTarget by remember { mutableStateOf<EmailMessage?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text("Unified Inbox", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                var localMessage by remember(message.id) { mutableStateOf(message) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* open */ },
                    color = if (localMessage.isUnread) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (localMessage.isUnread) 1.dp else 0.dp
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Text(text = localMessage.from.first().uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Text(text = localMessage.from, fontWeight = if (localMessage.isUnread) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text(text = localMessage.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Text(text = localMessage.subject, fontWeight = if (localMessage.isUnread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            androidx.compose.material3.Text(text = localMessage.body, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                viewModel.emailRepository.markAsRead(listOf(message.id))
                            }
                        }) { Icon(Icons.Default.Email, contentDescription = "Toggle read") }
                    }
                }
                HorizontalDivider()
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
fun ComposeEmailScreen(
    accountId: String,
    viewModel: MainViewModel,
    onSend: () -> Unit
) {
    var to by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var bcc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text("New Message") },
                navigationIcon = {
                    IconButton(onClick = onSend) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (to.isNotBlank() && subject.isNotBlank()) {
                            coroutineScope.launch {
                                val from = viewModel.getDefaultAccount()?.email.orEmpty()
                                val sender = EmailAddress(name = from.substringBefore("@"), email = from)
                                val recipients = EmailRecipients(
                                    to = to.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { EmailAddress(it, it) },
                                    cc = cc.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { EmailAddress(it, it) },
                                    bcc = bcc.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { EmailAddress(it, it) }
                                )
                                val email = Email(
                                    accountId = accountId,
                                    folder = "Sent",
                                    uid = java.util.UUID.randomUUID().toString(),
                                    messageId = java.util.UUID.randomUUID().toString(),
                                    threadId = java.util.UUID.randomUUID().toString(),
                                    sender = sender,
                                    recipients = recipients,
                                    subject = subject,
                                    bodyText = body,
                                    sentAt = kotlinx.datetime.Clock.System.now()
                                )
                                viewModel.emailRepository.insert(email)
                                onSend()
                            }
                        }
                    }) { Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send") }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(value = to, onValueChange = { to = it }, label = { Text("To") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = cc, onValueChange = { cc = it }, label = { Text("CC") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = bcc, onValueChange = { bcc = it }, label = { Text("BCC") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 8)
        }
    }
}

private fun Email.toEmailMessage(): EmailMessage {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
    val ldt = java.time.LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(receivedAt.toEpochMilliseconds()),
        java.time.ZoneId.systemDefault()
    )
    return EmailMessage(
        id = id,
        from = sender.name ?: sender.email,
        subject = subject,
        body = bodyText.orEmpty().take(120),
        time = formatter.format(ldt),
        isUnread = isUnread(),
        accountColor = Color.Unspecified
    )
}
