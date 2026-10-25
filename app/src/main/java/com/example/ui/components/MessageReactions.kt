package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.GhostMessage
import com.example.data.model.TelegramReactionUser
import com.example.data.remote.TelegramReactionText
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageReactions(
    message: GhostMessage,
    onLoadUsers: suspend (GhostMessage, String) -> Result<List<TelegramReactionUser>>
) {
    val summary = message.reactions
    if (summary.isNullOrBlank()) return
    var selected by remember(message.telegramChatId, message.telegramMsgId) { mutableStateOf<Pair<String, String>?>(null) }
    var users by remember(message.telegramChatId, message.telegramMsgId) { mutableStateOf<List<TelegramReactionUser>>(emptyList()) }
    var error by remember(message.telegramChatId, message.telegramMsgId) { mutableStateOf<String?>(null) }
    var loading by remember(message.telegramChatId, message.telegramMsgId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    selected?.let { (key, label) ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("$label reactors") }, text = {
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text(error!!)
                users.isEmpty() -> Text("No reactors are available for this reaction.")
                else -> LazyColumn {
                    items(users, key = { it.id }) { user ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                            Text(user.name, style = MaterialTheme.typography.bodyLarge)
                            user.username?.let { Text("@$it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }

    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        summary.split(TelegramReactionText.SEPARATOR).filter { it.isNotBlank() }.forEach { entry ->
            val parts = entry.split('\u001f', limit = 2)
            val labelText = parts.getOrNull(1) ?: entry.substringBefore(" · ")
            val customId = Regex("Custom emoji (\\d+)").find(labelText)?.groupValues?.getOrNull(1)
            val key = parts.firstOrNull().orEmpty().takeIf { parts.size == 2 }
                ?: customId?.let { "custom:$it" }
                ?: if (labelText.startsWith("⭐")) "paid" else "emoji:${labelText.takeWhile { !it.isWhitespace() }}"
            val label = customId?.let { "Custom emoji $it" } ?: labelText.takeWhile { !it.isWhitespace() }
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable {
                    selected = key to label
                    loading = true
                    error = null
                    users = emptyList()
                    scope.launch {
                        onLoadUsers(message, key).fold(
                            onSuccess = { users = it },
                            onFailure = { error = it.message ?: "Couldn't load reaction users." }
                        )
                        loading = false
                    }
                }) {
                Text(labelText, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
