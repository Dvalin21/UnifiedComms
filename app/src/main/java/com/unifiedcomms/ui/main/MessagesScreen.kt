package com.unifiedcomms.ui.main
import androidx.compose.foundation.border

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.AlertDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.ConversationType
import com.unifiedcomms.data.model.getCurrentUserId
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
@Suppress("unused")
fun MessagesScreen(
    viewModel: MainViewModel,
    onConversationClick: (String) -> Unit,
    onNewMessage: () -> Unit
) {
    val currentUserId = com.unifiedcomms.data.model.getCurrentUserId()
    val context = LocalContext.current
    // ponytail: chat is local + scoped to the primary account only (no per-account
    // chat switching). Show the primary account's display NAME, not the raw email.
    // If the account name is itself email-shaped, use the local part (testbox) so we
    // don't render an address in the chat UI.
    val accountName = viewModel.getDefaultAccount()?.name?.let { if (it.contains("@")) it.substringBefore("@") else it } ?: "Chat"
    val conversations by viewModel.messagingRepository.getAllConversationsForUser(currentUserId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val displayConversations = remember(conversations, accountName) { conversations.map { it.toMockConversation(accountName, context) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNewMessage) {
                        Icon(Icons.Default.Add, contentDescription = "New message")
                    }
                }
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (displayConversations.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 96.dp, bottom = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No messages yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start a conversation with the + button",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(displayConversations) { conversation ->
                ConversationListItem(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation.id) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ConversationScreen(
    viewModel: MainViewModel,
    conversationId: String,
    onBack: () -> Unit
) {
    var conversation by remember { mutableStateOf<Conversation?>(null) }
    val currentUserId = com.unifiedcomms.data.model.getCurrentUserId()
    val context = LocalContext.current
    val accountName = viewModel.getDefaultAccount()?.name?.let { if (it.contains("@")) it.substringBefore("@") else it } ?: "Chat"
    LaunchedEffect(conversationId) {
        conversation = viewModel.messagingRepository.getConversationById(conversationId)
        com.unifiedcomms.util.MessagingForegroundGate.setOpen(conversationId, true)
    }
    val messages by viewModel.messagingRepository.getMessagesByConversation(conversationId, 100, 0)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val displayMessages = remember(messages) { messages.map { it.toMockMessage(context) } }
    val displayConversation = remember(conversationId, conversation, accountName) {
        conversation?.toMockConversation(accountName, context, messages)
            ?: MockConversation(conversationId, "Unknown", "", "No conversation", "", false, 0, ConversationType.DIRECT)
    }
    DisposableEffect(conversationId) {
        onDispose { com.unifiedcomms.util.MessagingForegroundGate.setOpen(conversationId, false) }
    }
    LaunchedEffect(messages, conversationId) {
        val unread = messages.filter { it.recipientId == currentUserId && it.status != com.unifiedcomms.data.model.MessageStatus.READ }
        if (unread.isNotEmpty()) {
            runCatching { viewModel.messagingRepository.markConversationRead(conversationId, currentUserId) }
            runCatching { viewModel.messagingRepository.markMessagesRead(unread.map { it.id }) }
        }
    }
    var messageText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = displayConversation.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Chat",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayMessages.forEach { msg ->
                    MessageBubble(message = msg, isCurrentUser = msg.isOutgoing)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).fillMaxWidth(),
                        placeholder = { Text("Message") },
                        singleLine = true
                    )
                    if (messageText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.sendMessage(conversationId = conversationId, content = messageText)
                                }
                                messageText = ""
                            },
                            enabled = messageText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationListItem(
    conversation: MockConversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar: colored circle with initial (chat-app standard).
        val avatarColor = Color(abs(conversation.id.hashCode() % 0xFFFFFF) + 0xFF000000)
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(avatarColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (conversation.name.firstOrNull()?.uppercase() ?: "?"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Middle: name + last-message preview.
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.name,
                    fontWeight = if (conversation.isUnread) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = conversation.time,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.lastMessage.ifBlank { "No messages" },
                    fontSize = 14.sp,
                    color = if (conversation.isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                            .widthIn(min = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: MockMessage, isCurrentUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isCurrentUser) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = "J",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // ponytail: cap bubble at 78% of the screen width (and 340dp absolute) so it
        // never stretches edge-to-edge on phones nor gets absurdly wide on tablets.
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .widthIn(max = 340.dp),
            shape = if (isCurrentUser) {
                RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            } else {
                RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            },
            color = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Column(modifier = Modifier.padding(10.dp, 8.dp)) {
                Text(
                    text = message.content,
                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )
                Text(
                    text = message.timestamp,
                    fontSize = 10.sp,
                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                )
            }
        }
    }
}

data class MockConversation(
    val id: String,
    val name: String,
    val email: String,
    val lastMessage: String,
    val time: String,
    val isUnread: Boolean,
    val unreadCount: Int = 0,
    val type: ConversationType
)

data class MockMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val isOutgoing: Boolean,
    val timestamp: String
)

fun getMockConversations(): List<MockConversation> = emptyList()

@Suppress("unused")
fun getMockMessages(_conversationId: String): List<MockMessage> = emptyList()

@Composable
fun ComposeMessageScreen(
    viewModel: MainViewModel,
    conversationId: String? = null,
    onSend: () -> Unit = {}
) {
    var recipient by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Message") },
                navigationIcon = {
                    IconButton(onClick = onSend) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("To") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6
            )
            Button(
                onClick = {
                    val trimmed = recipient.trim()
                    if (trimmed.isBlank() || body.isBlank()) return@Button
                    coroutineScope.launch {
                        val targetConversationId = conversationId ?: run {
                            val currentUserId = com.unifiedcomms.data.model.getCurrentUserId()
                            val participants = listOf(currentUserId, trimmed)
                            viewModel.messagingRepository.findDirectConversation(
                                participants,
                                ConversationType.DIRECT
                            )?.id ?: run {
                                // ponytail: no existing conversation — create one (#18).
                                val newId = java.util.UUID.randomUUID().toString()
                                viewModel.messagingRepository.insertConversation(
                                    Conversation(id = newId, participantIds = participants, participantNames = emptyMap())
                                )
                                newId
                            }
                        }
                        viewModel.sendMessage(targetConversationId, body)
                    }
                    onSend()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = recipient.isNotBlank() && body.isNotBlank()
            ) {
                Text("Send")
            }
        }
    }
}

private fun Conversation.toMockConversation(accountName: String, context: android.content.Context, messages: List<Message> = emptyList()): MockConversation = MockConversation(
    id = id,
    // ponytail: a direct chat is scoped to the primary account, so show the
    // account display name rather than the raw peer email address.
    name = accountName,
    email = getOtherParticipantNames(getCurrentUserId()).firstOrNull().orEmpty(),
    lastMessage = messages.lastOrNull()?.content.orEmpty(),
    time = formatChatTime(context, lastActivityAt),
    isUnread = unreadCount > 0,
    unreadCount = unreadCount,
    type = type
)

private fun Message.toMockMessage(context: android.content.Context): MockMessage = MockMessage(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    content = content,
    isOutgoing = isOutgoing(),
    timestamp = formatChatTime(context, sentAt)
)

// ponytail: chat timestamps must read like a chat (1:43 PM), not ISO-with-millis
// (13:13:44.282). Today -> h:mm a (respects the device 12/24h setting),
// yesterday -> "Yesterday", else short date.
private fun formatChatTime(context: android.content.Context, instant: kotlinx.datetime.Instant): String {
    val zdt = java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(java.time.ZoneId.systemDefault())
    val now = java.time.LocalDate.now()
    val date = zdt.toLocalDate()
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return when {
        date == now -> zdt.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
        date == now.minusDays(1) -> "Yesterday"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
    }
}
