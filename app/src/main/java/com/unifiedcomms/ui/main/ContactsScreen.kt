package com.unifiedcomms.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unifiedcomms.data.model.ContactSource
import com.unifiedcomms.data.model.UnifiedContact
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ContactsScreen(
    viewModel: MainViewModel,
    onContactClick: (String) -> Unit,
    onAddContact: () -> Unit
) {
    val contacts by viewModel.getAllContacts().collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Contacts") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Icon(Icons.Default.Person, contentDescription = "Add contact")
            }
        }
    ) { innerPadding ->
        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text("No contacts yet", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactRow(contact = contact, onClick = { onContactClick(contact.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: UnifiedContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(contact.getInitials(), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(contact.displayName.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleSmall)
            val sub = contact.emails.firstOrNull() ?: contact.phoneNumbers.firstOrNull()
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (contact.source == ContactSource.CARDDAV) {
            Icon(Icons.Default.Sync, contentDescription = "Synced", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(end = 4.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactEditScreen(
    viewModel: MainViewModel,
    contactId: String? = null,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeAccounts = accounts.filter { it.isActive }

    // ponytail: load existing contact once if editing.
    var existing by remember { mutableStateOf<UnifiedContact?>(null) }
    LaunchedEffect(contactId) {
        if (contactId != null) existing = viewModel.contactRepository.getById(contactId)
    }

    var displayName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf<String?>(null) }
    var lastName by remember { mutableStateOf<String?>(null) }
    var organization by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf<String?>(null) }
    var emailsText by remember { mutableStateOf("") }
    var phonesText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf<String?>(null) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Seed fields from existing contact when loaded.
    LaunchedEffect(existing) {
        existing?.let { c ->
            displayName = c.displayName
            firstName = c.firstName
            lastName = c.lastName
            organization = c.organization
            title = c.title
            emailsText = c.emails.joinToString("\n")
            phonesText = c.phoneNumbers.joinToString("\n")
            notes = c.notes
            selectedAccountId = c.accountId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing != null) "Edit Contact" else "New Contact") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = {
                            scope.launch {
                                val r = viewModel.deleteContact(existing!!)
                                if (r.success) onDone() else error = r.errorMessage
                            }
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                    IconButton(enabled = !saving && displayName.isNotBlank(), onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            val emails = emailsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val phones = phonesText.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val account = selectedAccountId?.let { viewModel.getAccountById(it) }
                            val built = (existing ?: UnifiedContact(displayName = displayName)).copy(
                                displayName = displayName,
                                firstName = firstName?.takeIf { it.isNotBlank() },
                                lastName = lastName?.takeIf { it.isNotBlank() },
                                organization = organization?.takeIf { it.isNotBlank() },
                                title = title?.takeIf { it.isNotBlank() },
                                emails = emails,
                                phoneNumbers = phones,
                                notes = notes?.takeIf { it.isNotBlank() },
                                accountId = account?.id,
                                source = if (account != null) ContactSource.CARDDAV else ContactSource.LOCAL,
                                updatedAt = kotlinx.datetime.Clock.System.now()
                            )
                            saving = false
                            val ok: Boolean
                            val errMsg: String?
                            if (existing != null) {
                                val sr = viewModel.updateContact(built)
                                ok = sr.success; errMsg = sr.errorMessage
                            } else {
                                val cr = viewModel.createContact(built)
                                ok = cr.success; errMsg = cr.error
                            }
                            if (ok) onDone() else error = errMsg
                        }
                    }) { Icon(Icons.Default.Save, contentDescription = "Save") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(firstName ?: "", { firstName = it }, label = { Text("First") }, modifier = Modifier.weight(1f))
                OutlinedTextField(lastName ?: "", { lastName = it }, label = { Text("Last") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(organization ?: "", { organization = it }, label = { Text("Organization") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(title ?: "", { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                emailsText, { emailsText = it }, label = { Text("Emails (one per line)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            OutlinedTextField(
                phonesText, { phonesText = it }, label = { Text("Phones (one per line)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            OutlinedTextField(notes ?: "", { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            // Account selector: ties the contact to its owning CardDAV account.
            Text("Account", style = MaterialTheme.typography.labelMedium)
            if (activeAccounts.isEmpty()) {
                Text("No accounts — contact will be stored locally.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccountChip("Local", selectedAccountId == null) { selectedAccountId = null }
                    activeAccounts.forEach { acc ->
                        AccountChip(acc.email, selectedAccountId == acc.id) { selectedAccountId = acc.id }
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AccountChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
