package com.unifiedcomms.ui.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unifiedcomms.R
import com.unifiedcomms.ui.theme.UnifiedCommsTheme

class SearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnifiedCommsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SearchScreen()
                }
            }
        }
    }
}

@Composable
fun SearchScreen() {
    val queryState = remember { mutableStateOf<String>("") }
    val context = LocalContext.current

    TopAppBar(
        title = { SearchField(queryState) },
        navigationIcon = {
            IconButton(onClick = { (context as? android.app.Activity)?.finish() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
fun SearchField(queryState: androidx.compose.runtime.MutableState<String>) {
    val query = mutableStateOf(queryState.value)
    queryState.value = query.value
    TextField(
        value = query.value,
        onValueChange = { newValue -> query.value = newValue },
        placeholder = { Text("Search emails, events, tasks, messages") },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        singleLine = true,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}