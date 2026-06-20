package com.unifiedcomms.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unifiedcomms.ui.theme.UnifiedCommsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnifiedCommsTheme {
                val navController = rememberNavController()
                val viewModel: MainViewModel = viewModel()

                NavHost(navController, startDestination = "unified_inbox") {
                    composable("unified_inbox") {
                        UnifiedInboxScreen(
                            viewModel = viewModel,
                            onNavigateToEmail = { accountId, folder -> navController.navigate("email/$accountId/$folder") },
                            onNavigateToCalendar = { navController.navigate("calendar") },
                            onNavigateToTasks = { navController.navigate("tasks") },
                            onNavigateToMessages = { navController.navigate("messages") },
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToAddAccount = { navController.navigate("add_account") }
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
                            viewModel = viewModel,
                            onSend = { navController.popBackStack() }
                        )
                    }
                    composable("calendar") {
                        CalendarScreen(
                            viewModel = viewModel,
                            onCreateEvent = { navController.navigate("create_event") },
                            onEventClick = { event -> navController.navigate("event_detail/${event.id}") }
                        )
                    }
                    composable("create_event") {
                        CreateEventScreen(
                            viewModel = viewModel,
                            onSave = { navController.popBackStack() }
                        )
                    }
                    composable("event_detail/{eventId}") { backStackEntry ->
                        val eventId = backStackEntry.arguments?.getString("eventId").orEmpty()
                        EventDetailScreen(
                            viewModel = viewModel,
                            eventId = eventId,
                            onEdit = { navController.navigate("edit_event/$eventId") }
                        )
                    }
                    composable("tasks") {
                        TasksScreen(
                            viewModel = viewModel,
                            onCreateTask = { navController.navigate("create_task") },
                            onTaskClick = { task -> navController.navigate("task_detail/${task.id}") }
                        )
                    }
                    composable("create_task") {
                        CreateTaskScreen(
                            viewModel = viewModel,
                            onSave = { navController.popBackStack() }
                        )
                    }
                    composable("messages") {
                        MessagesScreen(
                            onConversationClick = { conv -> navController.navigate("conversation/${conv.id}") },
                            onNewMessage = { navController.navigate("new_message") }
                        )
                    }
                    composable("conversation/{conversationId}") { backStackEntry ->
                        val conversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
                        ConversationScreen(
                            conversationId = conversationId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onAddAccount = { navController.navigate("add_account") },
                            onAccountClick = { account -> navController.navigate("account_settings/${account.id}") }
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
                }
            }
        }
    }
}
