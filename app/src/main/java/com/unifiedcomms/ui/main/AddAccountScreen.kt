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

/**
 * Provider chips shown first. Choosing OAuth launches AddAccountActivity (which owns
 * the browser + token exchange). Choosing a manual/IMAP type stays on this screen
 * and triggers autodiscover from the email address before revealing advanced fields.
 */
private data class Provider(
    val label: String,
    val type: AccountType,
    val oauth: Boolean,
    val monogram: String,
    val color: Long
)

// Brand-colored monogram tiles (F-Droid-friendly: no trademarked raster logos).
// To use real brand SVGs instead: add res/drawable/ic_provider_<x>.xml and swap the
// ProviderTile Surface content for an Image(painter = painterResource(...)).
private val PROVIDERS = listOf(
    Provider("Google", AccountType.GOOGLE, true, "G", 0xFF4285F4),
    Provider("Outlook", AccountType.OUTLOOK, true, "O", 0xFF0078D4),
    Provider("Yahoo", AccountType.YAHOO, true, "Y", 0xFF6001D2),
    Provider("iCloud", AccountType.ICLOUD, true, "i", 0xFF3693F3),
    Provider("Mailcow", AccountType.MAILCOW, false, "M", 0xFF4A6FE3),
    Provider("Exchange", AccountType.EXCHANGE, false, "E", 0xFF0072C6),
    Provider("ProtonMail", AccountType.PROTONMAIL, false, "P", 0xFF8B89ED),
    Provider("Fastmail", AccountType.FASTMAIL, false, "F", 0xFF3B5BDB),
    Provider("Zoho", AccountType.ZOHO, false, "Z", 0xFFE03A1D),
    Provider("GMX", AccountType.GMX, false, "G", 0xFFEC1C24),
    Provider("AOL", AccountType.AOL, false, "A", 0xFF0060AF),
    Provider("Generic IMAP/SMTP", AccountType.GENERIC_IMAP_SMTP, false, "@", 0xFF6750A4),
    Provider("Generic CalDAV/CardDAV", AccountType.GENERIC_CALDAV_CARDDAV, false, "D", 0xFF00897B),
    Provider("Custom", AccountType.CUSTOM, false, "+", 0xFF625B71)
)

@Composable
private fun ProviderTile(
    provider: Provider,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color(provider.color)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = 2.dp,
        modifier = Modifier
            .size(64.dp)
            .semantics { contentDescription = provider.label }
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = provider.monogram, color = fg, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
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
                caldavUrl = d.caldavUrl ?: "$addr/dav/"
                carddavUrl = d.carddavUrl ?: "$addr/dav/"
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
                        val serverConfig = when (type) {
                            AccountType.MAILCOW -> ServerConfig(
                                imapHost = advancedImapHost ?: server,
                                imapPort = imapPort,
                                imapUseSsl = imapUseSsl,
                                smtpHost = advancedSmtpHost ?: server,
                                smtpPort = smtpPort,
                                smtpUseStartTls = smtpUseStartTls,
                                caldavUrl = caldavUrl.trim().ifBlank { "$server/dav/" },
                                carddavUrl = carddavUrl.trim().ifBlank { "$server/dav/" }
                            )
                            AccountType.GENERIC_IMAP_SMTP -> ServerConfig(
                                imapHost = advancedImapHost ?: server,
                                imapPort = imapPort,
                                imapUseSsl = imapUseSsl,
                                smtpHost = advancedSmtpHost ?: server,
                                smtpPort = smtpPort,
                                smtpUseStartTls = smtpUseStartTls,
                                caldavUrl = caldavUrl.trim().ifBlank { null },
                                carddavUrl = carddavUrl.trim().ifBlank { null }
                            )
                            else -> ServerConfig(
                                imapHost = advancedImapHost ?: server,
                                imapPort = imapPort,
                                imapUseSsl = imapUseSsl,
                                smtpHost = advancedSmtpHost ?: server,
                                smtpPort = smtpPort,
                                smtpUseStartTls = smtpUseStartTls,
                                caldavUrl = caldavUrl.trim().ifBlank { "$server/dav/" },
                                carddavUrl = carddavUrl.trim().ifBlank { "$server/dav/" }
                            )
                        }
                        val account = Account(
                            name = name.ifBlank { trimmed },
                            email = trimmed,
                            accountType = type,
                            serverConfig = serverConfig,
                            authConfig = AuthConfig.AppPassword(trimmed, password),
                            syncConfig = SyncConfig.Defaults(),
                            uiConfig = UIConfig.Defaults()
                        )
                        coroutineScope.launch {
                            // ponytail: surface real sync failure instead of swallowing it.
                            try {
                                // addAccount() throws on DB failure; success = no exception.
                                viewModel.addAccount(account)
                                saving = false
                                val result = viewModel.syncAccount(account)
                                if (result.success) {
                                    saved = true
                                } else {
                                    error = "Account saved, but sync failed: ${result.errorMessage}"
                                }
                            } catch (e: Exception) {
                                saving = false
                                error = "Sync error: ${e.message ?: e::class.simpleName}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && !saved
                ) {
                    Text(if (saving) "Saving…" else if (saved) "Saved" else "Save")
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
