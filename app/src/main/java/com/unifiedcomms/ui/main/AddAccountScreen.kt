package com.unifiedcomms.ui.main

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unifiedcomms.ui.theme.UnifiedCommsTheme

@Composable
fun AddAccountScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    UnifiedCommsTheme {
        androidx.compose.material3.Scaffold { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding).padding(16.dp)
            ) {
                androidx.compose.material3.Text("Add Account")
            }
        }
    }
}
