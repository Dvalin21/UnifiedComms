package com.unifiedcomms.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
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
    val messages = emails.map { it.toEmailMessage() }
    // ponytail: tint the email avatar with the owning account color so a unified inbox is
    // scannable by account at a glance. Fall back to theme primary when the account is gone.
    val accountColor by remember {
        derivedStateOf { viewModel.accounts.value.firstOrNull { it.id == accountId }?.uiConfig?.color }
    }
    val avatarColor = remember(accountColor) { Color(accountColor ?: 0xFF2196F3.toInt()) }

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
                var localMessage by remember(message.id) { mutableStateOf(message) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEmailClick(localMessage.id) },
                    color = if (localMessage.isUnread) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (localMessage.isUnread) 1.dp else 0.dp
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(avatarColor, CircleShape), contentAlignment = Alignment.Center) {
                            Text(text = (localMessage.from.firstOrNull()?.uppercase() ?: "?"), fontWeight = FontWeight.Bold, color = Color.White)
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
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    viewModel.deleteEmails(listOf(message.id), resolvedFolder)
                                }
                            }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
                HorizontalDivider()
            }
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
    var error by remember { mutableStateOf<String?>(null) }
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
                        if (to.isNotBlank()) {
                            coroutineScope.launch {
                                val from = viewModel.getDefaultAccount()?.email.orEmpty()
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
                                    threadId = java.util.UUID.randomUUID().toString(),
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
            if (error != null) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
            }
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
        body = bodyText.orEmpty().stripHtml().take(120),
        time = formatter.format(ldt),
        isUnread = isUnread(),
        accountColor = Color.Unspecified
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailScreen(
    emailId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf<Email?>(null) }

    LaunchedEffect(emailId) {
        coroutineScope.launch {
            val loaded = viewModel.emailRepository.getById(emailId)
            if (loaded != null && loaded.isUnread()) {
                viewModel.emailRepository.markAsRead(listOf(emailId))
            }
            email = loaded
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(email?.subject ?: "Email", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { innerPadding ->
        val e = email
        if (e == null) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                Text("Email not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            android.util.Log.e("INVITEUI", "INVITE_NULL=${e.invite == null} subject='${e.subject.take(40)}' hasHtml=${!e.bodyHtml.isNullOrBlank()} hasText=${!e.bodyText.isNullOrBlank()}")
            if (e.invite != null) android.util.Log.e("INVITEUI", "INVITE_PARSED title='${e.invite.eventTitle.take(30)}' start=${e.invite.startAt} tz='${e.invite.timezone}'")
            val context = LocalContext.current
            LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
                item {
                    Text(e.sender.toString(), fontWeight = FontWeight.Bold)
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
                        // ponytail: render HTML when available (GMail/Samsung-style),
                        // fall back to plaintext. WebView is the correct renderer for
                        // arbitrary email HTML; JS is disabled and no network access.
                        if (!e.bodyHtml.isNullOrBlank()) {
                            val html = e.bodyHtml
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 600.dp),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = false
                                        settings.blockNetworkImage = false
                                        settings.blockNetworkLoads = true
                                        isVerticalScrollBarEnabled = false
                                        isHorizontalScrollBarEnabled = false
                                    }.also { wv ->
                                        wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                                    }
                                },
                                update = { wv -> wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) }
                            )
                        } else {
                            Text(e.bodyText ?: "(no content)", style = MaterialTheme.typography.bodyLarge)
                        }
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
    }
}

@Composable
private fun AttachmentRow(attachment: com.unifiedcomms.data.model.Attachment, onOpen: () -> Unit) {
    val sizeLabel = if (attachment.sizeBytes > 0) {
        "  ·  %.1f KB".format(attachment.sizeBytes / 1024.0)
    } else ""
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
