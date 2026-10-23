package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) { draft = withContext(Dispatchers.IO) { store.load() }; loaded = true }
    CompactDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { DialogHeading("Gemini summaries", { if (!saving) onDismiss() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Brief explanations in Amharic or English, using your own Gemini API key.", style = MaterialTheme.typography.bodySmall)
                if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedTextField(value = draft.apiKey, onValueChange = { draft = draft.copy(apiKey = it.trim()); error = null },
                    enabled = loaded && !saving, label = { Text("Gemini API key") }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Hide API key" else "Show API key")
                    } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { runCatching { uri.openUri("https://aistudio.google.com/apikey") } }) { Text("Get a key in Google AI Studio") }
                Text("Summary language", style = MaterialTheme.typography.labelMedium)
                SummaryLanguages(draft.language) { draft = draft.copy(language = it) }
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide model" else "Model: ${draft.model}", style = MaterialTheme.typography.labelSmall) }
                if (advanced) OutlinedTextField(value = draft.model, onValueChange = { draft = draft.copy(model = it.trim()) },
                    label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Choose a model available on your project's free tier.") })
                Text("Only messages you explicitly select are sent to Google. Free-tier content may be used to improve Google's products. " +
                    "Keep your project on the free tier to avoid usage charges. Quotas and availability can change.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your key is encrypted on this device and excluded from backups. Clear the field and save to remove it.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(enabled = loaded && !saving, onClick = {
            scope.launch {
                saving = true
                try { withContext(Dispatchers.IO) { store.save(draft) }; onDismiss() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { error = "Couldn't save. Check the key and model ID, then try again." }
                finally { saving = false }
            }
        }) { Text(if (saving) "Saving..." else "Save") } })
}

@Composable
internal fun SummaryLanguages(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Auto", "Amharic", "English").forEach { language ->
            FilterChip(selected = selected == language, onClick = { onSelect(language) },
                label = { Text(language, style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.weight(1f))
        }
    }
}
