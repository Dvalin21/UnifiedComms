package com.unifiedcomms.ui.main
import androidx.compose.material.icons.filled.ArrowBack

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun MessagesScreen(
    onConversationClick: (MockConversation) -> Unit,
    onNewMessage: () -> Unit
) {
    val conversations = remember { mutableStateOf<List<MockConversation>>(getMockConversations()) }

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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewMessage,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New message")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(conversations.value) { conversation ->
                ConversationListItem(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ConversationScreen(
    conversationId: String,
    onBack: () -> Unit
) {
    val conversation = getMockConversations().find { it.id == conversationId }
        ?: MockConversation("1", "John Doe", "john@example.com", "Hey! How are you?", "10:30 AM", false, 0, com.unifiedcomms.data.model.ConversationType.DIRECT)

    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateOf<List<MockMessage>>(getMockMessages(conversationId)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = conversation.name, fontWeight = FontWeight.Bold)
                        Text(text = "Online", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { }) { Icon(Icons.Default.Call, contentDescription = "Call") }
                    IconButton(onClick = { }) { Icon(Icons.Default.Videocam, contentDescription = "Video call") }
                    IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
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
                messages.value.forEach { msg ->
                    MessageBubble(message = msg, isCurrentUser = msg.isOutgoing)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach")
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar invite")
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Checklist, contentDescription = "Share task")
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Email, contentDescription = "Share email")
                    }

                    androidx.compose.material3.TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).fillMaxWidth(),
                        placeholder = { Text("Message") },
                        singleLine = true
                    )

                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice message")
                    }

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                val newMsg = MockMessage(
                                    id = "${messages.value.size + 1}",
                                    conversationId = conversationId,
                                    senderId = "me",
                                    content = messageText,
                                    isOutgoing = true,
                                    timestamp = java.time.LocalTime.now().toString()
                                )
                                messages.value = messages.value + newMsg
                                messageText = ""
                            }
                        },
                        enabled = messageText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = conversation.name.first().uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = Color(abs(conversation.id.hashCode() % 0xFFFFFF) + 0xFF000000),
                            shape = CircleShape
                        ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column {
                        Text(
                            text = conversation.name,
                            fontWeight = if (conversation.isUnread) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = conversation.lastMessage,
                            fontSize = 14.sp,
                            color = if (conversation.isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = conversation.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .widthIn(min = 24.dp)
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

        Surface(
            modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = 300.dp),
            shape = if (isCurrentUser) {
                RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            } else {
                RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            },
            color = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
                Text(text = message.timestamp, fontSize = 10.sp, color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (isCurrentUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = "Me",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        ),
                    textAlign = TextAlign.Center
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
    val unreadCount: Int,
    val type: com.unifiedcomms.data.model.ConversationType
)

data class MockMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val isOutgoing: Boolean,
    val timestamp: String
)

fun getMockConversations(): List<MockConversation> = listOf(
    MockConversation("1", "John Doe", "john@example.com", "Hey! How are you?", "10:30 AM", true, 2, com.unifiedcomms.data.model.ConversationType.DIRECT),
    MockConversation("2", "Jane Smith", "jane@company.com", "Meeting moved to 3pm", "9:15 AM", false, 0, com.unifiedcomms.data.model.ConversationType.DIRECT),
    MockConversation("3", "Team UnifiedComms", "team@unifiedcomms.app", "Release v1.0 is live!", "Yesterday", false, 0, com.unifiedcomms.data.model.ConversationType.GROUP),
    MockConversation("4", "Alice Chen", "alice@startup.io", "Can you review the PR?", "2 days ago", true, 1, com.unifiedcomms.data.model.ConversationType.DIRECT),
    MockConversation("5", "Support", "support@unifiedcomms.app", "Welcome to UnifiedComms!", "3 days ago", false, 0, com.unifiedcomms.data.model.ConversationType.BROADCAST)
)

fun getMockMessages(conversationId: String): List<MockMessage> = listOf(
    MockMessage("1", conversationId, "them", "Hey! How are you doing?", false, "10:25 AM"),
    MockMessage("2", conversationId, "me", "I'm doing great! Thanks for asking.", true, "10:26 AM"),
    MockMessage("3", conversationId, "them", "That's awesome. Did you see the new calendar feature?", false, "10:27 AM"),
    MockMessage("4", conversationId, "me", "Yes! The color coding is really nice.", true, "10:28 AM"),
    MockMessage("5", conversationId, "them", "I agree. Want to test the messaging integration?", false, "10:29 AM")
)
