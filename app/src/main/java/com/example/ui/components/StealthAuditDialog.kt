package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.StealthStats

@Composable
fun StealthAuditDialog(stats: StealthStats, onDismiss: () -> Unit) {
    CompactDialog(onDismissRequest = onDismiss,
        title = { DialogHeading("Offline & privacy", onDismiss, "close_audit_dialog_icon") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Presence policy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(if (stats.isGhostActive) "Offline presence is requested while browsing." else "Check your account's offline presence settings.",
                    style = MaterialTheme.typography.bodyMedium)
                PrivacyDetail("Read acknowledgements", "Browsing does not deliberately mark messages as read.")
                PrivacyDetail("Your other Telegram sessions", "Another active client can change your account's online status.")
                PrivacyDetail("Local storage", "Added chats and messages are cached in app-private storage.")
                PrivacyDetail("Gemini summaries", "Only selected message text is sent after you tap the Gemini summary button. Local key sentences stay on your phone.")
                Text("This screen describes app behavior; it is not a live network audit.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_audit_button")) { Text("Done") } })
}

@Composable
private fun PrivacyDetail(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
