package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.summary.LocalMessageSummarizer
import com.example.data.summary.MessageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun MessageSummaryDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var summary by remember(text) { mutableStateOf<MessageSummary?>(null) }
    var error by remember(text) { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        try { summary = withContext(Dispatchers.Default) { LocalMessageSummarizer.summarize(text) } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Couldn't summarize this message. You can still read the original." }
    }
    CompactDialog(onDismissRequest = onDismiss,
        title = { Text("Message summary", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("On-device · Extractive summary", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("Key sentences from this message. No text leaves your phone.", style = MaterialTheme.typography.bodySmall)
                val result = summary
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    result == null -> CircularProgressIndicator(Modifier.size(24.dp))
                    else -> {
                        if (result.alreadyBrief) Text("This message is already brief.", style = MaterialTheme.typography.labelMedium)
                        result.sentences.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (result.truncated) Text("Long text was shortened for local processing. Check the original for context.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = summary != null, onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Summary", summary?.sentences?.joinToString("\n\n").orEmpty()))
            }) { Text("Copy") }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
