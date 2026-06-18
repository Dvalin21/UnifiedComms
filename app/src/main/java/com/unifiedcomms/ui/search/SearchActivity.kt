package com.unifiedcomms.ui.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
            androidx.compose.material3.IconButton(onClick = { (context as? android.app.Activity)?.finish() }) {
                androidx.compose.material3.Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
fun SearchField(queryState: androidx.compose.runtime.MutableState<String>) {
    val query = mutableStateOf(queryState.value)
    queryState.value = query.value
    androidx.compose.material3.TextField(
        value = query.value as String,
        onValueChange = { newValue: String -> query.value = newValue },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        singleLine = true,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}