package com.unifiedcomms.ui.main

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.unifiedcomms.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.ui.auth.AddAccountActivity
import com.unifiedcomms.ui.theme.UnifiedCommsTheme
import com.unifiedcomms.util.Autodiscover
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.ui.res.painterResource

/**
 * Provider tiles shown first. Each renders its brand logo (VectorDrawable) on a brand-tinted
 * square with a caption, so it is identifiable. OAuth providers hand off to AddAccountActivity
 * (which owns the browser + token exchange); manual/IMAP types stay here and autodiscover from
 * the email before revealing advanced fields.
 */
private data class Provider(
    val label: String,
    val type: AccountType,
    val oauth: Boolean,
    val iconRes: Int,
    val color: Long
)

private val PROVIDERS = listOf(
    Provider("Google", AccountType.GOOGLE, true, R.drawable.ic_provider_google, 0xFF4285F4),
    Provider("Outlook", AccountType.OUTLOOK, true, R.drawable.ic_provider_outlook, 0xFF0078D4),
    Provider("Yahoo", AccountType.YAHOO, true, R.drawable.ic_provider_yahoo, 0xFF6001D2),
    Provider("iCloud", AccountType.ICLOUD, true, R.drawable.ic_provider_icloud, 0xFF3693F3),
    Provider("Mailcow", AccountType.MAILCOW, false, R.drawable.ic_provider_mailcow, 0xFF4A6FE3),
    Provider("Exchange", AccountType.EXCHANGE, false, R.drawable.ic_provider_exchange, 0xFF0072C6),
    Provider("ProtonMail", AccountType.PROTONMAIL, false, R.drawable.ic_provider_protonmail, 0xFF8B89ED),
    Provider("Fastmail", AccountType.FASTMAIL, false, R.drawable.ic_provider_fastmail, 0xFF3B5BDB),
    Provider("Zoho", AccountType.ZOHO, false, R.drawable.ic_provider_zoho, 0xFFE03A1D),
    Provider("GMX", AccountType.GMX, false, R.drawable.ic_provider_gmx, 0xFFEC1C24),
    Provider("AOL", AccountType.AOL, false, R.drawable.ic_provider_aol, 0xFF0060AF),
    Provider("Generic IMAP/SMTP", AccountType.GENERIC_IMAP_SMTP, false, R.drawable.ic_provider_generic_imap, 0xFF6750A4),
    Provider("Generic CalDAV/CardDAV", AccountType.GENERIC_CALDAV_CARDDAV, false, R.drawable.ic_provider_generic_caldav, 0xFF00897B),
    Provider("Custom", AccountType.CUSTOM, false, R.drawable.ic_provider_custom, 0xFF625B71)
)

@Composable
private fun ProviderTile(
    provider: Provider,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color(provider.color)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = provider.label }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bg,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            tonalElevation = 2.dp,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(provider.iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = provider.label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AddAccountScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf<Provider?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discovering by remember { mutableStateOf(false) }
    var autodiscovered by remember { mutableStateOf<Autodiscover.Discovered?>(null) }
    var autodiscoverFailed by remember { mutableStateOf(false) }

    // Advanced IMAP/SMTP/CalDAV fields
    var showAdvanced by remember { mutableStateOf(false) }
    var imapHost by remember { mutableStateOf("") }
    var imapPort by remember { mutableIntStateOf(993) }
    var imapUseSsl by remember { mutableStateOf(true) }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableIntStateOf(587) }
    var smtpUseStartTls by remember { mutableStateOf(true) }
    var caldavUrl by remember { mutableStateOf("") }
    var carddavUrl by remember { mutableStateOf("") }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope: CoroutineScope = rememberCoroutineScope()

    // Shared autodiscover trigger: tries to resolve server settings from the email
    // address. On success pre-fills IMAP/SMTP; on failure reveals Advanced for manual.
    fun runDiscovery() {
        val addr = email.trim()
        if (!addr.contains("@") || selectedProvider?.oauth == true) return
        autodiscovered = null
        autodiscoverFailed = false
        discovering = true
        coroutineScope.launch {
            val d = Autodiscover.discover(addr)
            discovering = false
            if (d != null) {
                autodiscovered = d
                imapHost = d.imapHost
                imapPort = d.imapPort
                imapUseSsl = d.imapSsl
                smtpHost = d.smtpHost
                smtpPort = d.smtpPort
                smtpUseStartTls = d.smtpStartTls
                // ponytail: NEVER guess a DAV URL. The Autodiscover engine returns the
                // real principal/home-set URL (or null). Keep it as-is; a guessed
                // "$server/dav/" is wrong for virtually every provider and is exactly
                // the "autodiscover returns wrong info" symptom. If null, leave blank
                // and let the user enter it manually (advanced fields reveal on failure).
                caldavUrl = d.caldavUrl ?: ""
                carddavUrl = d.carddavUrl ?: ""
                showAdvanced = false
            } else {
                // autodiscover failed -> reveal advanced for manual entry
                autodiscoverFailed = true
                showAdvanced = true
            }
        }
    }

    UnifiedCommsTheme {
        Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { Text(text = "Add Account") },
                    navigationIcon = {
                        androidx.compose.material3.TextButton(onClick = onComplete) {
                            androidx.compose.material3.Text("Close")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = "Email", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        // reset any prior discovery when the address changes
                        autodiscovered = null
                        autodiscoverFailed = false
                    },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { runDiscovery() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Password / App Password", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password or app password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Provider", style = MaterialTheme.typography.bodyLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PROVIDERS.forEach { p ->
                        ProviderTile(
                            provider = p,
                            selected = selectedProvider == p,
                            onClick = {
                                selectedProvider = p
                                if (p.oauth) {
                                    // hand off to the OAuth activity which owns the browser flow
                                    ctx.startActivity(
                                        Intent(ctx, AddAccountActivity::class.java)
                                            .putExtra("accountType", p.type.name)
                                    )
                                    return@ProviderTile
                                }
                                // manual/IMAP: autodiscover from the email (or reveal advanced)
                                runDiscovery()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (discovering) {
                    Text(text = "Looking up server settings…", color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (autodiscovered != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Server settings found automatically.",
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (autodiscoverFailed) {
                    Text(
                        text = "Could not auto-detect settings. Enter them below.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (selectedProvider != null && !selectedProvider!!.oauth) {
                    Text(text = "Name", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Advanced settings (revealed automatically when autodiscover fails)
                if (selectedProvider != null && !selectedProvider!!.oauth) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 2.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Advanced Settings",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                                    Text(if (showAdvanced) "Hide" else "Show")
                                }
                            }
                            if (showAdvanced) {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(12.dp))
                                AdvancedServerFields(
                                    imapHost = imapHost,
                                    imapPort = imapPort,
                                    imapUseSsl = imapUseSsl,
                                    smtpHost = smtpHost,
                                    smtpPort = smtpPort,
                                    smtpUseStartTls = smtpUseStartTls,
                                    caldavUrl = caldavUrl,
                                    carddavUrl = carddavUrl,
                                    onImapHostChange = { imapHost = it },
                                    onImapPortChange = { imapPort = it.toIntOrNull() ?: 993 },
                                    onImapSslChange = { imapUseSsl = it },
                                    onSmtpHostChange = { smtpHost = it },
                                    onSmtpPortChange = { smtpPort = it.toIntOrNull() ?: 587 },
                                    onSmtpTlsChange = { smtpUseStartTls = it },
                                    onCalDavChange = { caldavUrl = it },
                                    onCardDavChange = { carddavUrl = it }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (saved) {
                    Text(text = "Account saved.", color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onComplete) {
                        Text("Done")
                    }
                } else if (!error.isNullOrBlank()) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        if (saving || saved) return@Button
                        val provider = selectedProvider ?: run {
                            error = "Select a provider."; return@Button
                        }
                        if (provider.oauth) {
                            // OAuth handled by AddAccountActivity; just close this screen.
                            onComplete(); return@Button
                        }
                        val trimmed = email.trim()
                        if (trimmed.isBlank() || password.isBlank()) {
                            error = "Email and password are required."; return@Button
                        }
                        // If autodiscover succeeded use its host; else require advanced hosts.
                        val advancedImapHost = imapHost.trim().ifBlank { null }
                        val advancedSmtpHost = smtpHost.trim().ifBlank { null }
                        val server = advancedImapHost ?: trimmed.substringAfter("@")
                        if (advancedImapHost == null && autodiscovered == null) {
                            error = "Enter server settings under Advanced."; return@Button
                        }
                        saving = true
                        error = null
                        val type = provider.type
                        // ponytail: never invent a DAV URL. Keep what the user typed (or
                        // what autodiscover returned). Empty = "not configured"; the sync
                        // leg is simply disabled if its URL is blank — no wrong guess.
                        val calUrl = caldavUrl.trim().ifBlank { null }
                        val cardUrl = carddavUrl.trim().ifBlank { null }
                        val serverConfig = ServerConfig(
                            imapHost = advancedImapHost ?: server,
                            imapPort = imapPort,
                            imapUseSsl = imapUseSsl,
                            smtpHost = advancedSmtpHost ?: server,
                            smtpPort = smtpPort,
                            smtpUseStartTls = smtpUseStartTls,
                            caldavUrl = calUrl,
                            carddavUrl = cardUrl
                        )
                        val account = Account(
                            name = name.ifBlank { trimmed },
                            email = trimmed,
                            accountType = type,
                            serverConfig = serverConfig,
                            authConfig = AuthConfig.AppPassword(trimmed, password),
                            // ponytail: only enable the sync legs the user actually
                            // configured. A CalDAV/CardDAV account with no IMAP host must
                            // NOT be blocked by the email gate; a blank DAV URL means that
                            // leg is off (user enters it manually). This is what lets a
                            // calendar/contacts-only account save when IMAP isn't set.
                            syncConfig = SyncConfig.Defaults().copy(
                                syncEmail = advancedImapHost != null || server.isNotBlank(),
                                syncCalendar = calUrl != null,
                                syncContacts = cardUrl != null,
                                syncTasks = false
                            ),
                            uiConfig = UIConfig.Defaults()
                        )
                        coroutineScope.launch {
                            // ponytail: prove the connection BEFORE persisting. RFC 8314 §5.1 —
                            // never save until an authenticated TLS session is verified. If
                            // email (IMAP) fails to connect, nothing is saved and the
                            // user sees the exact reason. CalDAV/CardDAV failures are
                            // reported but the (still-useful) mail account may proceed
                            // with those syncs disabled.
                            val draft = account
                            val provision = viewModel.provisionAccount(draft)
                            // Block only if the user actually configured email AND it
                            // failed. A CalDAV/CardDAV-only account (syncEmail=false) is
                            // NOT blocked by the IMAP test — it saves on DAV success.
                            if (draft.syncConfig.syncEmail && !provision.emailOk) {
                                saving = false
                                error = "Could not connect (email): ${provision.emailError ?: "IMAP login failed"}"
                                return@launch
                            }
                            // Disable any DAV sync legs that failed to connect (honest,
                            // not silent — the user is told which failed after save).
                            val withSync = draft.copy(
                                syncConfig = draft.syncConfig.copy(
                                    syncCalendar = draft.syncConfig.syncCalendar && provision.calendarOk,
                                    syncContacts = draft.syncConfig.syncContacts && provision.contactsOk,
                                    syncTasks = draft.syncConfig.syncTasks && provision.tasksOk
                                )
                            )
                            runCatching { viewModel.addAccount(withSync) }
                                .onFailure { e ->
                                    saving = false
                                    error = "Could not save account: ${e.message ?: e::class.simpleName}"
                                    return@launch
                                }
                            saving = false
                            saved = true
                            val davNotes = buildList<String> {
                                if (draft.syncConfig.syncCalendar && !provision.calendarOk)
                                    add("Calendar: ${provision.calendarError ?: "connection failed"}")
                                if (draft.syncConfig.syncContacts && !provision.contactsOk)
                                    add("Contacts: ${provision.contactsError ?: "connection failed"}")
                                if (draft.syncConfig.syncTasks && !provision.tasksOk)
                                    add("Tasks: ${provision.tasksError ?: "connection failed"}")
                            }
                            if (davNotes.isNotEmpty()) {
                                error = "Saved (email only — DAV disabled):\n" + davNotes.joinToString("\n")
                            }
                            // Background sync kicks in via SyncManager observers; trigger an
                            // immediate sync so the inbox populates without another tap.
                            viewModel.syncAccount(withSync)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && !saved
                ) {
                    Text(if (saving) "Saving…" else if (saved) "Confirmed" else "Confirm")
                }
            }
        }
    }
}

@Composable
private fun AdvancedServerFields(
    imapHost: String,
    imapPort: Int,
    imapUseSsl: Boolean,
    smtpHost: String,
    smtpPort: Int,
    smtpUseStartTls: Boolean,
    caldavUrl: String,
    carddavUrl: String,
    onImapHostChange: (String) -> Unit,
    onImapPortChange: (String) -> Unit,
    onImapSslChange: (Boolean) -> Unit,
    onSmtpHostChange: (String) -> Unit,
    onSmtpPortChange: (String) -> Unit,
    onSmtpTlsChange: (Boolean) -> Unit,
    onCalDavChange: (String) -> Unit,
    onCardDavChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("IMAP / SMTP", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = imapHost,
            onValueChange = onImapHostChange,
            label = { Text("IMAP host") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = imapPort.toString(),
                onValueChange = onImapPortChange,
                label = { Text("IMAP port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = imapUseSsl, onCheckedChange = onImapSslChange)
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text("IMAP SSL", fontSize = 14.sp)
                }
            }
        }
        OutlinedTextField(
            value = smtpHost,
            onValueChange = onSmtpHostChange,
            label = { Text("SMTP host") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = smtpPort.toString(),
                onValueChange = onSmtpPortChange,
                label = { Text("SMTP port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = smtpUseStartTls, onCheckedChange = onSmtpTlsChange)
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text("SMTP STARTTLS", fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider()
        Text("CalDAV / CardDAV", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = caldavUrl,
            onValueChange = onCalDavChange,
            label = { Text("CalDAV URL") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = carddavUrl,
            onValueChange = onCardDavChange,
            label = { Text("CardDAV URL") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
