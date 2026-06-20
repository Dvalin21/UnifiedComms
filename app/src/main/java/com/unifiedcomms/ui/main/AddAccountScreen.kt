package com.unifiedcomms.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
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
    var serverUrl by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val coroutineScope: CoroutineScope = rememberCoroutineScope()

    UnifiedCommsTheme {
        Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { Text(text = "Add Account") },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = onComplete) {
                            androidx.compose.material3.Text("✕")
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
                
                Text(text = "Server URL", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (saving) return@Button
                                saving = true
                                val trimmed = email.trim()
                                val account = when (selectedType) {
                                    AccountType.GOOGLE -> Account.createGoogle(email = trimmed)
                                    AccountType.MAILCOW -> Account.createMailcow(
                                        email = trimmed,
                                        serverUrl = serverUrl.trim()
                                    )
                                    AccountType.OUTLOOK -> Account.createExchange(
                                        email = trimmed,
                                        serverUrl = serverUrl.trim()
                                    )
                                    else -> Account(
                                        name = trimmed,
                                        email = trimmed,
                                        accountType = selectedType,
                                        serverConfig = ServerConfig(),
                                        authConfig = AuthConfig.OAuth2(),
                                        syncConfig = SyncConfig.Defaults(),
                                        uiConfig = UIConfig.Defaults()
                                    )
                                }
                                coroutineScope.launch {
                                    viewModel.addAccount(account)
                                    saving = false
                                    onComplete()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving && email.isNotBlank()
                        ) {
                            Text(if (saving) "Saving..." else "Save")
                        }
            }
        }
    }
}
