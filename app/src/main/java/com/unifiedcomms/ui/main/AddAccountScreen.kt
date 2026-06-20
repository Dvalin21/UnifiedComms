package com.unifiedcomms.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unifiedcomms.ui.theme.UnifiedCommsTheme

@Composable
fun AddAccountScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    UnifiedCommsTheme {
        androidx.compose.material3.Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { androidx.compose.material3.Text(text = "Add Account") },
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
            ) {
                androidx.compose.material3.Text("Add Account implementation pending")
            }
        }
    }
}
