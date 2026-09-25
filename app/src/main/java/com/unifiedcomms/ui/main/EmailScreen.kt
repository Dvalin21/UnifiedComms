package com.unifiedcomms.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.stripHtml
import com.unifiedcomms.data.model.EmailAddress
import com.unifiedcomms.data.model.EmailRecipients
import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.data.model.CalendarInviteMessage
import com.unifiedcomms.ui.theme.AccountColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScreen(
    viewModel: MainViewModel,
    accountId: String,
    folder: String,
    onNavigateBack: () -> Unit,
    onCompose: () -> Unit,
    onEmailClick: (emailId: String) -> Unit
) {
    // ponytail: honor the real folder name from the drawer. Only INBOX is
    // case-insensitive; a custom folder (Archive, Junk, ...) must pass through
    // verbatim or the list would wrongly show INBOX contents.
    val resolvedFolder = when {
        folder.equals("INBOX", ignoreCase = true) -> "INBOX"
        else -> folder
    }
    val emails by viewModel.emailRepository
        .getByAccountAndFolder(accountId, resolvedFolder, 100, 0)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val messages = emails.map { it.toEmailMessage(resolvedFolder) }
    LaunchedEffect(accountId, resolvedFolder) {
        if (!resolvedFolder.equals("INBOX", ignoreCase = true) && emails.isEmpty()) {
            viewModel.syncFolder(accountId, resolvedFolder)
        }
    }
    // Match the unified inbox's stable per-account palette instead of every folder
    // row falling back to Material blue.
    val avatarColor = remember(accountId) { AccountColors.getColorForAccount(accountId).container }
    val folderTitle = if (resolvedFolder.equals("INBOX", ignoreCase = true)) "Inbox" else resolvedFolder

    var deleteTarget by remember { mutableStateOf<EmailMessage?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text(folderTitle, fontWeight = FontWeight.Bold) },
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
    ) { innerPadding ->
        actionError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(top = 96.dp, bottom = 96.dp),
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
                    text = "No emails yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Emails for this folder will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
        LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            items(messages) { message ->
                val localMessage = message
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEmailClick(localMessage.id) },
                    color = if (localMessage.isUnread) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (localMessage.isUnread) 1.dp else 0.dp
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(avatarColor, CircleShape), contentAlignment = Alignment.Center) {
                            Text(text = localMessage.initials, fontWeight = FontWeight.Bold, color = Color.White)
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
                                    emails.firstOrNull { it.id == message.id }?.let { model ->
                                        val result = viewModel.setEmailFlags(
                                            model,
                                            model.flags.copy(isRead = !model.flags.isRead)
                                        )
                                        actionError = if (result.success) null else result.errorMessage
                                    }
                                }
                            }) {
                                Icon(
                                    if (localMessage.isUnread) Icons.Default.MarkEmailRead else Icons.Default.MarkEmailUnread,
                                    contentDescription = if (localMessage.isUnread) "Mark read" else "Mark unread"
                                )
                            }
                            IconButton(onClick = { deleteTarget = message }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                    }
                }
                HorizontalDivider()
            }
        }
        }
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete email?") },
                text = { Text(target.subject) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        coroutineScope.launch {
                            val result = viewModel.deleteEmails(listOf(target.id), resolvedFolder)
                            actionError = if (result.success) null else result.errorMessage
                        }
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
            )
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
    val initials: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeEmailScreen(
    accountId: String,
    viewModel: MainViewModel,
    onSend: () -> Unit,
    mode: String? = null,
    sourceEmailId: String? = null
) {
    var to by rememberSaveable { mutableStateOf("") }
    var cc by rememberSaveable { mutableStateOf("") }
    var bcc by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sourceEmail by remember { mutableStateOf<Email?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sourceEmailId) {
        if (sourceEmailId.isNullOrBlank()) return@LaunchedEffect
        val source = viewModel.emailRepository.getById(sourceEmailId) ?: return@LaunchedEffect
        sourceEmail = source
        when (mode) {
            "reply" -> {
                to = source.sender.email
                subject = if (source.subject.startsWith("Re:", ignoreCase = true)) source.subject else "Re: ${source.subject}"
                body = buildString {
                    appendLine()
                    appendLine("On ${source.receivedAt}, ${source.sender} wrote:")
                    appendLine()
                    append(squashBlankLines(source.bodyText.orEmpty()).lines().joinToString("\n") { ">$it" })
                }
            }
            "forward" -> {
                subject = if (source.subject.startsWith("Fwd:", ignoreCase = true)) source.subject else "Fwd: ${source.subject}"
                body = buildString {
                    appendLine()
                    appendLine("Forwarded message:")
                    appendLine("From: ${source.sender}")
                    appendLine("Subject: ${source.subject}")
                    appendLine()
                    append(squashBlankLines(source.bodyText.orEmpty()))
                }
            }
        }
    }

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
                        if (to.isBlank()) {
                            error = "Enter at least one recipient"
                            return@IconButton
                        }
                        coroutineScope.launch {
                                val account = viewModel.getAccountById(accountId)
                                    ?: viewModel.getDefaultAccount()
                                val from = account?.email.orEmpty()
                                if (from.isBlank()) {
                                    error = "Sender account is unavailable"
                                    return@launch
                                }
                                val sender = EmailAddress(name = from.substringBefore("@"), email = from)
                                // Evidence: JavaMail InternetAddress ctor with a bare address as the
                                // display NAME produces an invalid RCPT TO -> 501 5.1.3. Parse with
                                // InternetAddress.parse() (RFC822) and use address as email, name only
                                // when one is present. Mirrors K-9 recipient handling.
                                fun parse(raw: String): List<EmailAddress> =
                                    raw.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }.flatMap { token ->
                                        try {
                                            javax.mail.internet.InternetAddress.parse(token).map { a ->
                                                EmailAddress(name = a.personal ?: "", email = a.address ?: "")
                                            }.filter { it.email.isNotBlank() }
                                        } catch (_: Exception) { emptyList() }
                                    }
                                val recipients = EmailRecipients(
                                    to = parse(to),
                                    cc = parse(cc),
                                    bcc = parse(bcc)
                                )
                                val email = Email(
                                    accountId = accountId,
                                    folder = "Sent",
                                    uid = java.util.UUID.randomUUID().toString(),
                                    messageId = "<${java.util.UUID.randomUUID()}@unifiedcomms.local>",
                                    threadId = sourceEmail?.threadId ?: java.util.UUID.randomUUID().toString(),
                                    inReplyTo = if (mode == "reply") sourceEmail?.messageId else null,
                                    references = if (mode == "reply") {
                                        sourceEmail?.references.orEmpty() + listOfNotNull(sourceEmail?.messageId)
                                    } else emptyList(),
                                    sender = sender,
                                    recipients = recipients,
                                    subject = subject,
                                    bodyText = body,
                                    sentAt = kotlinx.datetime.Clock.System.now()
                                )
                                val result = viewModel.sendEmail(email)
                                if (result.success) {
                                    onSend()
                                } else {
                                    error = result.errorMessage ?: "Send failed"
                                }
                            }
                }) { Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(value = to, onValueChange = { to = it }, label = { Text("To") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = cc, onValueChange = { cc = it }, label = { Text("CC") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = bcc, onValueChange = { bcc = it }, label = { Text("BCC") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 8)
            if (error != null) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun Email.toEmailMessage(folder: String): EmailMessage {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
    val ldt = java.time.LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(receivedAt.toEpochMilliseconds()),
        java.time.ZoneId.systemDefault()
    )
    val outgoing = folder.equals("Sent", ignoreCase = true) || folder.equals("Drafts", ignoreCase = true)
    val contact = if (outgoing) recipients.to.firstOrNull() else sender
    val from = if (outgoing) {
        contact?.let { "To: ${it.name ?: it.email}" } ?: "No recipient"
    } else {
        contact?.name ?: contact?.email.orEmpty()
    }
    return EmailMessage(
        id = id,
        from = from,
        subject = subject,
        body = preview.ifBlank { bodyText.orEmpty() }.stripHtml().take(120),
        time = formatter.format(ldt),
        isUnread = isUnread(),
        initials = contact?.getInitials() ?: "?"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailScreen(
    emailId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onReply: (Email) -> Unit = {},
    onForward: (Email) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf<Email?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    LaunchedEffect(emailId) {
        coroutineScope.launch {
            val loaded = viewModel.emailRepository.getById(emailId)
            email = loaded
            if (loaded != null && loaded.isUnread()) {
                // Render cached content immediately; the IMAP flag write must not
                // block opening a message when the network is unavailable.
                coroutineScope.launch {
                    val updated = loaded.copy(flags = loaded.flags.copy(isRead = true))
                    val result = viewModel.setEmailFlags(loaded, updated.flags)
                    if (result.success) email = updated else actionError = result.errorMessage
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(email?.subject ?: "Email", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    email?.let { message ->
                        IconButton(onClick = { onReply(message) }) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply")
                        }
                        IconButton(onClick = { onForward(message) }) {
                            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = "Forward")
                        }
                        Box {
                            IconButton(onClick = { moreOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More email actions")
                            }
                            DropdownMenu(
                                expanded = moreOpen,
                                onDismissRequest = { moreOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (message.flags.isFlagged) "Remove star" else "Star") },
                                    leadingIcon = {
                                        Icon(
                                            if (message.flags.isFlagged) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        moreOpen = false
                                        coroutineScope.launch {
                                            val updated = message.copy(flags = message.flags.copy(isFlagged = !message.flags.isFlagged))
                                            val result = viewModel.setEmailFlags(message, updated.flags)
                                            if (result.success) email = updated else actionError = result.errorMessage
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (message.flags.isRead) "Mark unread" else "Mark read") },
                                    leadingIcon = {
                                        Icon(
                                            if (message.flags.isRead) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        moreOpen = false
                                        coroutineScope.launch {
                                            val updated = message.copy(flags = message.flags.copy(isRead = !message.flags.isRead))
                                            val result = viewModel.setEmailFlags(message, updated.flags)
                                            if (result.success) email = updated else actionError = result.errorMessage
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                    onClick = {
                                        moreOpen = false
                                        coroutineScope.launch {
                                            val result = viewModel.moveEmails(listOf(message.id), message.folder, "Archive")
                                            if (result.success) onBack() else actionError = result.errorMessage
                                        }
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete email")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val e = email
        actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (e == null) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                Text("Email not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val context = LocalContext.current
            LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
                item {
                    val senderName = e.sender.name?.takeIf { it.isNotBlank() }
                    Text(senderName ?: e.sender.email, fontWeight = FontWeight.Bold)
                    if (senderName != null && e.sender.email.isNotBlank()) {
                        Text(
                            e.sender.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(e.subject, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm")
                            .format(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.receivedAt.toEpochMilliseconds()), java.time.ZoneId.systemDefault())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    // ponytail: render a clean invite card when the email carries a parsed
                    // text/calendar invite. This is the actionable surface — Accept/Decline/Add.
                    e.invite?.let { inv ->
                        InviteCard(invite = inv, viewModel = viewModel)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    // ponytail: when the email carries a parsed invite, the InviteCard above
                    // is the actionable surface. The raw body (HTML/ICS blob starting with
                    // "OpenGroupware_org" / BEGIN:VCALENDAR) is noise — don't render it.
                    if (e.invite == null) {
                        // ponytail: render the plaintext body in Compose. A WebView needs a
                        // fixed height (Compose cannot measure WebView content), which left a
                        // 600dp empty block on short messages, and it cannot follow the theme.
                        // HTML is only a fallback, stripped to text. Restore the WebView only if
                        // inline images or CSS layout become a requirement.
                        val body = e.bodyText?.takeIf { it.isNotBlank() }
                            ?: e.bodyHtml?.takeIf { it.isNotBlank() }
                                ?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }
                            ?: "(no content)"
                        Text(squashBlankLines(body), style = MaterialTheme.typography.bodyLarge)
                    }
                    if (e.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Attachments", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        e.attachments.forEach { att ->
                            AttachmentRow(
                                attachment = att,
                                onOpen = {
                                    coroutineScope.launch {
                                        val path = viewModel.downloadAttachment(e.accountId, e.folder, e.imapUid ?: e.uid, att)
                                        path?.let { openFile(context, it, att.mimeType) }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete email?") },
                text = { Text(email?.subject ?: "") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        email?.let { message ->
                            coroutineScope.launch {
                                val result = viewModel.deleteEmails(listOf(message.id), message.folder)
                                if (result.success) onBack() else actionError = result.errorMessage
                            }
                        }
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun AttachmentRow(attachment: com.unifiedcomms.data.model.Attachment, onOpen: () -> Unit) {
    val sizeLabel = if (attachment.sizeBytes > 0) "  ·  ${formatAttachmentSize(attachment.sizeBytes)}" else ""
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    attachment.mimeType + sizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InviteCard(invite: CalendarInviteMessage, viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var acceptResult by remember { mutableStateOf<String?>(null) }
    var declineResult by remember { mutableStateOf<String?>(null) }
    var addResult by remember { mutableStateOf<String?>(null) }
    val tz = if (invite.timezone.isBlank()) ZoneId.systemDefault() else runCatching { ZoneId.of(invite.timezone) }.getOrDefault(ZoneId.systemDefault())
    val start = runCatching { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(invite.startAt.toEpochMilliseconds()), tz) }.getOrDefault(LocalDateTime.now())
    val end = runCatching { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(invite.endAt.toEpochMilliseconds()), tz) }.getOrDefault(LocalDateTime.now())
    val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    invite.eventTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("${start.format(dateFmt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "${start.format(timeFmt)} – ${end.format(timeFmt)} (${invite.timezone.takeIf { it.isNotBlank() } ?: "local"})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            invite.location?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it.replace("\n", ", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            invite.organizerName?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Organizer: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            acceptResult = if (viewModel.respondToInvite(invite, AttendeeStatus.ACCEPTED)) "Accepted" else "Accept failed"
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Accept", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            declineResult = if (viewModel.respondToInvite(invite, AttendeeStatus.DECLINED)) "Declined" else "Decline failed"
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Decline", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            addResult = if (viewModel.addInviteToCalendar(invite)) "Added to calendar" else "Add failed"
                            busy = false
                        }
                    },
                    enabled = !busy,
                    // ponytail: +Just Add is two words; give it a bit more width so it fits on
                    // one line without clipping (Accept/Decline stay at 1f).
                    modifier = Modifier.weight(1.3f)
                ) {
                    Text("+Just Add", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
            }
            StatusRow("Accepted", acceptResult, MaterialTheme.colorScheme.primary)
            StatusRow("Declined", declineResult, MaterialTheme.colorScheme.primary)
            StatusRow("Added", addResult, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusRow(labelPrefix: String, result: String?, successColor: androidx.compose.ui.graphics.Color) {
    if (result == null) return
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = result,
        color = if (result.endsWith("failed")) MaterialTheme.colorScheme.onSurfaceVariant else successColor,
        maxLines = 1,
        softWrap = false
    )
}

private fun formatAttachmentSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${value.toLong()} ${units[unit]}" else "%.1f %s".format(value, units[unit])
}

// ponytail: mail bodies arrive with CR-only line breaks and runs of blank lines
// (quoted headers, trailing <br><br>&nbsp;). Compose only breaks on \n, so normalize
// and collapse 3+ blank lines to one, the way mail clients render quoted text.
// Upgrade to collapsible quote blocks if the squashed quote ever needs to be hidden.
internal fun squashBlankLines(raw: String): String =
    raw.replace(Regex("\r\n?"), "\n")
        .trimEnd('\n', ' ', '\u00A0')
        .replace(Regex("\n{3,}"), "\n\n")

private fun openFile(context: android.content.Context, path: String, mimeType: String) {
    runCatching {
        val file = java.io.File(path)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val type = if (mimeType.isNotBlank()) mimeType else MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open attachment"))
    }.onFailure { e ->
        android.util.Log.e("EmailScreen", "openFile failed: ${e.message}")
    }
}
