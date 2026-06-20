package com.unifiedcomms.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.ui.theme.UnifiedCommsTheme

@Composable
fun AccountSettingsScreen(
    viewModel: MainViewModel,
    accountId: String,
    onBack: () -> Unit
) {
    UnifiedCommsTheme {
        androidx.compose.material3.Scaffold { innerPadding ->
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(innerPadding).padding(16.dp)
            ) {
                androidx.compose.material3.Text("Account Settings: $accountId")
            }
        }
    }
}
