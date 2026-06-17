package com.unifiedcomms.ui.search

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.ArrowBack
import androidx.compose.material3.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.unifiedcomms.R
import com.unifiedcomms.ui.theme.UnifiedCommsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
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
    var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    
    androidx.compose.material3.TopAppBar(
        title = {
            androidx.compose.material3.TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search emails, events, tasks, messages") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = { query = "" }) {
                            androidx.compose.material3.Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        },
        navigationIcon = {
            androidx.compose.material3.IconButton(onClick = { (androidx.activity.ComponentActivity)@thisContext.finish() }) {
                androidx.compose.material3.Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
    )
}