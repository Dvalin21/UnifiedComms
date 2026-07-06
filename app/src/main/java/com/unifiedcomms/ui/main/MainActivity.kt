@file:Suppress("UNUSED_PARAMETER")

package com.unifiedcomms.ui.main

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unifiedcomms.ui.theme.UnifiedCommsTheme
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import com.unifiedcomms.util.PreferencesManager

enum class BiometricLockState { LOCKED, UNLOCKED }

@Composable
private fun BiometricLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val biometricManager = remember { BiometricManager.from(context) }
    var statusMessage by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { androidx.compose.material3.Text("Biometric Lock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(statusMessage.ifBlank { "Unlock to access UnifiedComms." })
                if (activity != null && biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                    androidx.compose.material3.Button(onClick = {
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock UnifiedComms")
                            .setSubtitle("Authenticate to continue")
                            .setNegativeButtonText("Cancel")
                            .build()
                        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                onUnlocked()
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                super.onAuthenticationError(errorCode, errString)
                                statusMessage = errString.toString()
                            }

                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                                statusMessage = "Authentication failed."
                            }
                        })
                        biometricPrompt.authenticate(promptInfo)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Unlock")
                    }
                } else {
                    Text("Biometric not available on this device.", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {}
    )
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainPrefs = remember { PreferencesManager.getInstance() }
            val themeMode = mainPrefs.getString("theme_mode", "system")
            val systemDark = (getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager)?.nightMode == android.app.UiModeManager.MODE_NIGHT_YES
            val effectiveDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            UnifiedCommsTheme(darkTheme = effectiveDark) {
                val navController = rememberNavController()
                val viewModel: MainViewModel = viewModel()
                var biometricLockState by remember { mutableStateOf(BiometricLockState.LOCKED) }
                DisposableEffect(lifecycle, viewModel.syncManagerInstance) {
                    lifecycle.addObserver(viewModel.syncManagerInstance)
                    onDispose {
                        lifecycle.removeObserver(viewModel.syncManagerInstance)
                    }
                }

                val pendingTab = intent?.getStringExtra("navigate_to")?.let { raw ->
                    when (raw) {
                        "inbox", "unified_inbox" -> 0
                        "email" -> 1
                        "calendar" -> 2
                        "tasks" -> 3
                        "messages" -> 4
                        else -> null
                    }
                }
                LaunchedEffect(pendingTab) {
                    if (pendingTab != null) {
                        navController.popBackStack("unified_inbox", false)
                        viewModel.requestTab(pendingTab)
                    }
                }

                val prefs = remember { PreferencesManager.getInstance() }
                val biometricLockPref = prefs.getBoolean("biometric_lock", false)
                LaunchedEffect(biometricLockPref) {
                    biometricLockState = if (biometricLockPref) BiometricLockState.LOCKED else BiometricLockState.UNLOCKED
                }

                when (biometricLockState) {
                    BiometricLockState.LOCKED -> BiometricLockScreen(onUnlocked = { biometricLockState = BiometricLockState.UNLOCKED })
                    BiometricLockState.UNLOCKED -> {
                        NavHost(navController, startDestination = "unified_inbox") {
                            composable("unified_inbox") {
                                UnifiedInboxScreen(
                                    viewModel = viewModel,
                                    onNavigateToEmail = { accountId, folder -> navController.navigate("email/$accountId/$folder") },
                                    onNavigateToCalendar = { navController.navigate("calendar") },
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onNavigateToAddAccount = { navController.navigate("add_account") },
                                    onNavigateToConversation = { conversationId -> navController.navigate("conversation/$conversationId") },
                                    onNavigateToComposeMessage = { navController.navigate("compose_message") },
                                    onEventClick = { eventId -> navController.navigate("event_detail/$eventId") },
                                    initialTab = pendingTab
                                )
                            }
                            composable(
                                route = "email/{accountId}/{folder}",
                                arguments = listOf(
                                    androidx.navigation.navArgument("accountId") { type = androidx.navigation.NavType.StringType },
                                    androidx.navigation.navArgument("folder") { type = androidx.navigation.NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
                                val folder = backStackEntry.arguments?.getString("folder").orEmpty()
                                EmailScreen(
                                    viewModel = viewModel,
                                    accountId = accountId,
                                    folder = folder,
                                    onNavigateBack = { navController.popBackStack() },
                                    onCompose = { navController.navigate("compose_email/$accountId") }
                                )
                            }
                            composable("compose_email/{accountId}") { backStackEntry ->
                                val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
                                ComposeEmailScreen(
                                    accountId = accountId,
                                    viewModel = viewModel,
                                    onSend = { navController.popBackStack() }
                                )
                            }
                            composable("calendar") {
                                CalendarScreen(
                                    viewModel = viewModel,
                                    onCreateEvent = { navController.navigate("create_event") },
                                    onEventClick = { eventId -> navController.navigate("event_detail/$eventId") }
                                )
                            }
                            composable("create_event") {
                                val activeAccounts by viewModel.accounts.collectAsStateWithLifecycle()
                                CreateEventScreen(
                                    viewModel = viewModel,
                                    accountId = activeAccounts.filter { it.isActive }.firstOrNull()?.id.orEmpty(),
                                    onSave = { navController.popBackStack() }
                                )
                            }
                            composable("event_detail/{eventId}") { backStackEntry ->
                                val eventId = backStackEntry.arguments?.getString("eventId").orEmpty()
                                var event by remember { mutableStateOf<com.unifiedcomms.data.model.CalendarEvent?>(null) }
                                androidx.compose.runtime.LaunchedEffect(eventId) {
                                    event = viewModel.getEventById(eventId)
                                }
                                if (event == null) {
                                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Event not found") }
                                } else {
                                    EventDetailScreen(
                                        event = event!!,
                                        onEdit = { navController.navigate("edit_event/$eventId") },
                                        onBack = { navController.popBackStack() },
                                        onShare = { /* TODO: share event */ }
                                    )
                                }
                            }
                            composable("tasks") {
                                TasksScreen(
                                    viewModel = viewModel,
                                    onCreateTask = { navController.navigate("create_task") },
                                    onTaskClick = { task -> navController.navigate("task_detail/${task.id}") }
                                )
                            }
                            composable("create_task") {
                                val activeAccounts by viewModel.accounts.collectAsStateWithLifecycle()
                                CreateTaskScreen(
                                    viewModel = viewModel,
                                    accountId = activeAccounts.filter { it.isActive }.firstOrNull()?.id.orEmpty(),
                                    onSave = { navController.popBackStack() }
                                )
                            }
                            composable("messages") {
                                MessagesScreen(
                                    viewModel = viewModel,
                                    onConversationClick = { conversationId -> navController.navigate("conversation/$conversationId") },
                                    onNewMessage = { navController.navigate("compose_message") }
                                )
                            }
                            composable("conversation/{conversationId}") { backStackEntry ->
                                val conversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
                                ConversationScreen(
                                    viewModel = viewModel,
                                    conversationId = conversationId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onAddAccount = { navController.navigate("add_account") },
                                    onAccountClick = { account -> navController.navigate("account_settings/${account.id}") },
                                    onBack = { navController.popBackStack() },
                                    onEncryptionClick = { navController.navigate("encryption") }
                                )
                            }
                            composable("add_account") {
                                AddAccountScreen(
                                    viewModel = viewModel,
                                    onComplete = { navController.popBackStack() }
                                )
                            }
                            composable("account_settings/{accountId}") { backStackEntry ->
                                val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
                                AccountSettingsScreen(
                                    viewModel = viewModel,
                                    accountId = accountId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("compose_message") {
                                ComposeMessageScreen(
                                    viewModel = viewModel,
                                    onSend = { navController.popBackStack() }
                                )
                            }
                            composable("edit_event/{eventId}") { backStackEntry ->
                                val eventId = backStackEntry.arguments?.getString("eventId").orEmpty()
                                val accounts by viewModel.accounts.collectAsStateWithLifecycle()
                                CreateEventScreen(
                                    viewModel = viewModel,
                                    accountId = accounts.firstOrNull()?.id.orEmpty(),
                                    eventId = eventId,
                                    onSave = { navController.popBackStack() }
                                )
                            }
                            composable("task_detail/{taskId}") { backStackEntry ->
                                val taskId = backStackEntry.arguments?.getString("taskId").orEmpty()
                                val accounts by viewModel.accounts.collectAsStateWithLifecycle()
                                CreateTaskScreen(
                                    viewModel = viewModel,
                                    accountId = accounts.firstOrNull()?.id.orEmpty(),
                                    taskId = taskId,
                                    onSave = { navController.popBackStack() }
                                )
                            }
                            composable("encryption") {
                                EncryptionScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}
