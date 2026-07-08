package com.unifiedcomms.ui.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.unifiedcomms.UnifiedCommsApplication
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.UnifiedContact
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.ContactRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.ui.theme.UnifiedCommsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SearchRow(
    val kind: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

class SearchActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = UnifiedCommsApplication.getInstance()
        val db = app.database
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val taskRepo = TaskRepositoryImpl(db.taskDao(), db.taskListDao())
        val contactRepo = ContactRepositoryImpl(db.contactDao())
        val accountRepo = AccountRepositoryImpl(db.accountDao(), CryptoManagerImpl(this))

        setContent {
            UnifiedCommsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SearchScreen(
                        onClose = { finish() },
                        onSearch = { query ->
                            // ponytail: run each source query against the active account set, flatten results.
                            val rows = mutableListOf<SearchRow>()
                            if (query.isNotBlank()) {
                                val accountIds = accountRepo.getAllActive().first().map { it.id }
                                if (accountIds.isNotEmpty()) {
                                    val emails = emailRepo.searchEmails(query, accountIds, 50).first()
                                    rows += emails.map { e ->
                                        SearchRow("Email", Icons.Default.Email, e.subject, e.sender.name ?: e.sender.email)
                                    }
                                    val events = calendarRepo.searchEvents(query, accountIds, 50).first()
                                    rows += events.map { ev ->
                                        SearchRow("Calendar", Icons.Default.CalendarMonth, ev.title, ev.location ?: "")
                                    }
                                    val tasks = taskRepo.searchTasks(query, accountIds, 50).first()
                                    rows += tasks.map { t ->
                                        SearchRow("Task", Icons.Default.Checklist, t.title, if (t.isCompleted()) "Done" else "Open")
                                    }
                                }
                                val contacts = contactRepo.search(query, 50).first()
                                rows += contacts.map { c ->
                                    SearchRow("Contact", Icons.Default.Contacts, c.displayName, c.emails.firstOrNull() ?: "")
                                }
                            }
                            rows
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    onClose: () -> Unit,
    onSearch: suspend (String) -> List<SearchRow>
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchRow>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Search") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Query") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                singleLine = true,
                trailingIcon = {
                    androidx.compose.material3.TextButton(onClick = {
                        if (query.isBlank()) return@TextButton
                        searching = true
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val r = onSearch(query)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                results = r
                                searching = false
                            }
                        }
                    }) { Text(if (searching) "..." else "Go") }
                }
            )

            if (results.isEmpty() && query.isNotBlank() && !searching) {
                Text("No results.", style = MaterialTheme.typography.bodyMedium)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { row ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* future: drill into result */ },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(row.title, style = MaterialTheme.typography.bodyLarge)
                                if (row.subtitle.isNotBlank()) {
                                    Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(row.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
