package com.unifiedcomms.ui.main

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AddAccountScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    var selectedType by remember { mutableStateOf(AccountType.GOOGLE) }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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

    val coroutineScope: CoroutineScope = rememberCoroutineScope()

    UnifiedCommsTheme {
        Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { Text(text = "Add Account") },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = onComplete) {
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
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Add Account", style = MaterialTheme.typography.headlineSmall)

                Text(text = "Email", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Account type", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.values().forEach { type ->
                        val selected = selectedType == type
                        androidx.compose.material3.FilterChip(
                            selected = selected,
                            onClick = { selectedType = type },
                            label = { androidx.compose.material3.Text(type.name) }
                        )
                    }
                }

                if (selectedType in setOf(
                    AccountType.MAILCOW,
                    AccountType.OUTLOOK,
                    AccountType.EXCHANGE,
                    AccountType.CUSTOM,
                    AccountType.GENERIC_IMAP_SMTP,
                    AccountType.GENERIC_CALDAV_CARDDAV
                )) {
                    Text(text = "Server URL", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(text = "Name", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Password / App Password", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password or app password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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

                val ctx = androidx.compose.ui.platform.LocalContext.current
                Button(
                    onClick = {
                        if (saving || saved) return@Button
                        saving = true
                        error = null
                        val trimmed = email.trim()
                        val selected = selectedType
                        // OAuth providers require the web consent flow in AddAccountActivity.
                        // Building AppPassword locally here would silently break Google/Outlook/etc.
                        val oauthProviders = setOf(
                            AccountType.GOOGLE,
                            AccountType.OUTLOOK,
                            AccountType.YAHOO,
                            AccountType.ICLOUD
                        )
                        if (selected in oauthProviders) {
                            ctx.startActivity(
                                Intent(ctx, AddAccountActivity::class.java)
                                    .putExtra("accountType", selected.name)
                            )
                            saving = false
                            return@Button
                        }
                        val requiresServer = selected in setOf(
                            AccountType.MAILCOW,
                            AccountType.OUTLOOK,
                            AccountType.EXCHANGE,
                            AccountType.CUSTOM,
                            AccountType.GENERIC_IMAP_SMTP,
                            AccountType.GENERIC_CALDAV_CARDDAV
                        )
                        val server = serverUrl.trim()
                        if (trimmed.isBlank() || (requiresServer && server.isBlank())) {
                            error = "Email and server URL are required."
                            saving = false
                            return@Button
                        }
                        val advancedImapHost = imapHost.trim().ifBlank { null }
                        val advancedSmtpHost = smtpHost.trim().ifBlank { null }
                        val serverConfig = when (selected) {
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
                            accountType = selected,
                            serverConfig = serverConfig,
                            authConfig = AuthConfig.AppPassword(trimmed, password),
                            syncConfig = SyncConfig.Defaults(),
                            uiConfig = UIConfig.Defaults()
                        )
                        coroutineScope.launch {
                            val accountSaved = runCatching { viewModel.addAccount(account) }.isSuccess
                            saving = false
                            if (accountSaved) {
                                saved = true
                                try {
                                    viewModel.syncAccount(account)
                                } catch (_: Exception) { /* notification will surface failure */ }
                            } else {
                                error = "Failed to save account."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && !saved
                ) {
                    Text(if (saving) "Saving..." else if (saved) "Saved" else "Save")
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
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.weight(1f),
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
