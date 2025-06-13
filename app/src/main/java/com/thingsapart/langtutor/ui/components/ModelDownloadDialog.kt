package com.thingsapart.langtutor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thingsapart.langtutor.llm.LlmBackend
import com.thingsapart.langtutor.llm.LlmModelConfig

// Define a state holder for the dialog
data class ModelDownloadDialogState(
    val showDialog: Boolean = false,
    val modelName: String? = null, // Changed from modelInfo: LlmModelConfig?
    val progress: Float = 0f, // 0.0 to 100.0
    val errorMessage: String? = null,
    val isComplete: Boolean = false
)

@Composable
fun ModelDownloadDialog(
    state: ModelDownloadDialogState,
    onDismissRequest: () -> Unit, // Called when dialog is dismissed (e.g. back press, click outside)
    onRetry: () -> Unit, // Changed from onRetry: (LlmModelConfig?) -> Unit
    // onCancel: () -> Unit // Optional: if cancellation is supported during download
) {
    if (!state.showDialog) { // Removed modelInfo null check as it's no longer LlmModelConfig
        return
    }

    Dialog(
        onDismissRequest = {
            if (state.isComplete || state.errorMessage != null) {
                onDismissRequest()
            }
            // Else, typically don't dismiss if download is in progress unless explicitly cancelled
        },
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
    ) {
        Card(
            elevation = 8.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (state.isComplete) "Download Complete" else if (state.errorMessage != null) "Download Failed" else "Downloading Model",
                    style = MaterialTheme.typography.h6
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Model: ${state.modelName ?: "Unknown"}", style = MaterialTheme.typography.body1) // Updated text display
                Spacer(modifier = Modifier.height(16.dp))

                if (!state.isComplete && state.errorMessage == null) {
                    LinearProgressIndicator(
                        progress = state.progress / 100f, // Progress is 0.0 to 1.0 for this Composable
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "${state.progress.toInt()}%")
                }

                state.errorMessage?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Error: $it", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = { onRetry() }) { // Updated retry button's onClick
                            Text("Retry")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onDismissRequest) {
                            Text("Close")
                        }
                    }
                }

                if (state.isComplete) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDismissRequest) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

// Example Usage Preview (optional, but good for development)
@Composable
fun ModelDownloadDialogPreview_Downloading() {
    MaterialTheme { // Assuming your app has a MaterialTheme wrapper
        var state by remember {
            mutableStateOf(ModelDownloadDialogState(
                showDialog = true,
                modelName = "Gemma 2B Preview", // Updated
                progress = 45f
            ))
        }
        ModelDownloadDialog(
            state = state,
            onDismissRequest = { state = state.copy(showDialog = false) },
            onRetry = {} // Updated
        )
    }
}

@Composable
fun ModelDownloadDialogPreview_Error() {
    MaterialTheme {
        var state by remember {
            mutableStateOf(ModelDownloadDialogState(
                showDialog = true,
                modelName = "Gemma 2B Preview Error", // Updated
                progress = 30f,
                errorMessage = "Network connection lost."
            ))
        }
        ModelDownloadDialog(
            state = state,
            onDismissRequest = { state = state.copy(showDialog = false) },
            onRetry = {} // Updated
        )
    }
}

@Composable
fun ModelDownloadDialogPreview_Completed() {
    MaterialTheme {
        var state by remember {
            mutableStateOf(ModelDownloadDialogState(
                showDialog = true,
                modelName = "Gemma 2B Preview Completed", // Updated
                progress = 100f,
                isComplete = true
            ))
        }
        ModelDownloadDialog(
            state = state,
            onDismissRequest = { state = state.copy(showDialog = false) },
            onRetry = {} // Updated
        )
    }
}
