package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.summary.GeminiMessageSummarizer
import com.example.data.summary.GeminiSettings
import com.example.data.summary.GeminiSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GeminiSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val store = remember(context) { GeminiSettingsStore(context) }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(GeminiSettings()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) { draft = withContext(Dispatchers.IO) { store.load().copy(model = "auto") }; loaded = true }
    CompactDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { DialogHeading("Gemini summaries", { if (!saving) onDismiss() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Message summaries", style = MaterialTheme.typography.labelLarge)
                            Text("Short summaries in Amharic or English.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Connection", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = draft.apiKey, onValueChange = { draft = draft.copy(apiKey = it.trim()); error = null },
                        enabled = loaded && !saving, label = { Text("Gemini key") }, singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Hide key" else "Show key", Modifier.size(20.dp))
                        } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = !saving, onClick = { runCatching { uri.openUri("https://aistudio.google.com/apikey") } }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("Get a free key from Google AI Studio", style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Default.OpenInNew, null, Modifier.padding(start = 6.dp).size(14.dp))
                    }
                }
                Column {
                    Text("Default language", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    SummaryLanguages(draft.language, enabled = !saving) { draft = draft.copy(language = it) }
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.PrivacyTip, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Only the message you choose is shared with Google. Free-tier content may be used to improve its products.",
                            Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (saving || !loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(enabled = loaded && !saving, onClick = {
            scope.launch {
                saving = true; error = null
                try {
                    if (draft.apiKey.isNotBlank()) GeminiMessageSummarizer.verify(draft)
                    withContext(Dispatchers.IO) { store.save(draft) }
                    onDismiss()
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: java.io.IOException) { error = failure.message ?: "Couldn't connect. Try again." }
                catch (failure: IllegalArgumentException) { error = "Check your Gemini key and try again." }
                catch (failure: IllegalStateException) { error = failure.message ?: "Couldn't connect. Check your key." }
                catch (_: Exception) { error = "Couldn't save your settings. Please try again." }
                finally { saving = false }
            }
        }, shape = RoundedCornerShape(12.dp)) { Text(if (saving) "Checking..." else if (draft.apiKey.isBlank()) "Save" else "Connect & save") } })
}

@Composable
internal fun SummaryLanguages(selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Auto", "Amharic", "English").forEach { language ->
            FilterChip(selected = selected == language, enabled = enabled, onClick = { onSelect(language) },
                label = { Text(language, style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp))
        }
    }
}
