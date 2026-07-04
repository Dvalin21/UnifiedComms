package com.unifiedcomms.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Stable
interface SimpleDialogHost {
    fun show(
        title: String,
        message: String,
        positive: String = "OK",
        onPositive: () -> Unit,
        negative: String? = null,
        onNegative: (() -> Unit)? = null,
        dismissOnClickOutside: Boolean = true
    )
}

@Composable
fun rememberSimpleDialogHost(): SimpleDialogHost {
    val state = remember { mutableStateOf<DialogState?>(null) }
    val current = state.value
    if (current != null) {
        androidx.compose.runtime.LaunchedEffect(current) {
            current.show()
        }
    }
    return object : SimpleDialogHost {
        override fun show(
            title: String,
            message: String,
            positive: String,
            onPositive: () -> Unit,
            negative: String?,
            onNegative: (() -> Unit)?,
            dismissOnClickOutside: Boolean
        ) {
            state.value = DialogState(
                title = title,
                message = message,
                positive = positive,
                onPositive = {
                    state.value = null
                    onPositive()
                },
                negative = negative,
                onNegative = {
                    state.value = null
                    onNegative?.invoke()
                },
                dismissOnClickOutside = dismissOnClickOutside
            )
        }
    }

    current?.let { d ->
        AlertDialog(
            onDismissRequest = {
                if (d.dismissOnClickOutside) state.value = null
            },
            title = { Text(d.title) },
            text = { Text(d.message) },
            confirmButton = {
                TextButton(onClick = d.onPositive) { Text(d.positive) }
            },
            dismissButton = d.negative?.let {
                {
                    TextButton(onClick = d.onNegative ?: { state.value = null }) {
                        Text(it)
                    }
                }
            }
        )
    }
}

private data class DialogState(
    val title: String,
    val message: String,
    val positive: String,
    val onPositive: () -> Unit,
    val negative: String? = null,
    val onNegative: (() -> Unit)? = null,
    val dismissOnClickOutside: Boolean = true
)

object SimpleInfoDialog {
    private var pending: (() -> Unit)? = null

    fun show(
        activity: android.app.Activity,
        title: String,
        message: String,
        positive: String = "OK",
        onPositive: () -> Unit
    ) {
        pending = onPositive
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> pending?.invoke() }
            .setCancelable(true)
            .show()
    }
}
