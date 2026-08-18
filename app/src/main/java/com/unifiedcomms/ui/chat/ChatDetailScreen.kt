package com.unifiedcomms.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.ui.theme.AccountColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    peerPhone: String,
    peerName: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val messages by viewModel.messagesForPeer(peerPhone).collectAsStateWithLifecycle()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(peerName, fontWeight = FontWeight.Bold)
                        Text(
                            peerPhone,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                state = listState,
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No messages yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        peerPhone = peerPhone,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(peerPhone, messageText)
                                messageText = ""
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Default.Send,
                                contentDescription = "Send",
                                tint = if (messageText.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

data class ChatBubbleData(
    val id: String,
    val senderId: String,
    val content: String,
    val sentAt: Long,
    val status: MessageStatus,
)

@Composable
private fun ChatBubble(
    message: ChatMessage,
    peerPhone: String,
) {
    val currentUserId = com.unifiedcomms.util.PreferencesManager.getInstance()
        .getString("current_user_id", "current_user")
    val isOutgoing = message.senderId == currentUserId
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, RoundedCornerShape(16.dp))
                .padding(12.dp),
            contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Column {
                if (!isOutgoing) {
                    Text(
                        message.senderId.take(20),
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.6f),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    message.content,
                    color = textColor,
                    fontSize = 15.sp,
                    maxLines = 100,
                    softWrap = true,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formatMsgTime(message.sentAt),
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.5f),
                )
            }
        }
    }
}

private fun formatMsgTime(ts: Long): String {
    return try {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
    } catch (e: Exception) {
        ""
    }
}
