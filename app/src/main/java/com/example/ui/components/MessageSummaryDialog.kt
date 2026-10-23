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
import com.example.data.summary.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MessageSummaryDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { GeminiSettingsStore(context) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(GeminiSettings()) }
    var language by remember { mutableStateOf("Auto") }
    var setup by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var summary by remember(text) { mutableStateOf<String?>(null) }
    var label by remember(text) { mutableStateOf("") }
    var error by remember(text) { mutableStateOf<String?>(null) }
    LaunchedEffect(store, setup) {
        if (!setup) { settings = withContext(Dispatchers.IO) { store.load() }; language = settings.language; loaded = true }
    }
    if (setup) GeminiSettingsDialog(onDismiss = { setup = false })
    fun summarize(cloud: Boolean) {
        scope.launch {
            busy = true; error = null; summary = null
            try {
                if (cloud) {
                    summary = GeminiMessageSummarizer.summarize(text, settings.copy(language = language))
                    label = "Summary"
                } else {
                    val result = withContext(Dispatchers.Default) { LocalMessageSummarizer.summarize(text) }
                    summary = result.sentences.joinToString("\n\n")
                    label = if (result.truncated) "Shortened excerpts" else "Selected sentences"
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = when {
                !cloud -> "Couldn't extract text from this message."
                failure is java.io.IOException || failure is IllegalStateException || failure is IllegalArgumentException -> failure.message?.take(250) ?: "Couldn't create a summary. Please try again."
                else -> "Couldn't create a summary. Please try again."
            } }
            finally { busy = false }
        }
    }
    CompactDialog(onDismissRequest = onDismiss,
        title = { DialogHeading("Summarise message", onDismiss) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryLanguages(language, enabled = !busy) { language = it }
                Button(enabled = loaded && !busy, onClick = { if (settings.apiKey.isBlank()) setup = true else summarize(true) },
                    modifier = Modifier.fillMaxWidth()) { Text(if (settings.apiKey.isBlank()) "Connect Gemini" else "Summarise") }
                Text("Sends only this message's text or caption to Google. Free-tier content may be used to improve its products.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !busy, onClick = { summarize(false) }) { Text("Extract sentences", style = MaterialTheme.typography.labelMedium) }
                    TextButton(enabled = !busy, onClick = { setup = true }) { Text("Settings", style = MaterialTheme.typography.labelMedium) }
                }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Preparing summary...", style = MaterialTheme.typography.labelSmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                summary?.let {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        dismissButton = { TextButton(enabled = summary != null && !busy, onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Summary", summary.orEmpty()))
        }) { Text("Copy") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (busy) "Cancel" else "Done") } })
}
