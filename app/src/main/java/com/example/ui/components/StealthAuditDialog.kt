package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.ui.Alignment
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
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PrivacyTip, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (stats.isGhostActive) "Offline mode is enabled" else "Check your offline mode settings",
                            Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrivacyDetail("Reading messages", "Read receipts stay off while you browse here.")
                PrivacyDetail("Other Telegram apps", "Opening another Telegram app may change your online status.")
                PrivacyDetail("Saved on your phone", "Messages are saved on this device for later reading.")
                PrivacyDetail("Gemini summaries", "Only the message you choose is shared with Google when you summarise it.")
                }
                }
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
