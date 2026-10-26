package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GhostMessage
import com.example.data.model.TelegramReactionUser
import com.example.data.remote.TelegramReactionText

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageReactions(
    message: GhostMessage,
    onLoadUsers: suspend (GhostMessage, String) -> Result<List<TelegramReactionUser>>
) {
    val summary = message.reactions
    if (summary.isNullOrBlank()) return

    var selected by remember(message.telegramChatId, message.telegramMsgId) {
        mutableStateOf<Pair<String, String>?>(null)
    }
    var users by remember(message.telegramChatId, message.telegramMsgId) {
        mutableStateOf<List<TelegramReactionUser>>(emptyList())
    }
    var error by remember(message.telegramChatId, message.telegramMsgId) { mutableStateOf<String?>(null) }
    var loading by remember(message.telegramChatId, message.telegramMsgId) { mutableStateOf(false) }
    var requestVersion by remember(message.telegramChatId, message.telegramMsgId) { mutableIntStateOf(0) }

    selected?.let { reaction ->
        LaunchedEffect(message.telegramChatId, message.telegramMsgId, reaction.first, requestVersion) {
            loading = true
            users = emptyList()
            error = null
            val result = onLoadUsers(message, reaction.first)
            result.fold(
                onSuccess = { users = it },
                onFailure = { error = it.message ?: "Couldn't load reaction users." }
            )
            loading = false
        }

        CompactDialog(
            onDismissRequest = { selected = null },
            title = {
                DialogHeading("Reactions", onDismiss = { selected = null }, tag = "close_reactors_dialog")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)) {
                                Text(reaction.second, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Reacted with ${reaction.second}", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        loading -> "Loading people…"
                                        error != null -> "Couldn’t load the list"
                                        else -> "${users.size} ${if (users.size == 1) "person" else "people"}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.Favorite, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                        }
                    }

                    when {
                        loading -> Box(Modifier.fillMaxWidth().heightIn(min = 150.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                Text("Fetching reactors", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        error != null -> Box(Modifier.fillMaxWidth().heightIn(min = 130.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(error!!, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { requestVersion++ }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Try again")
                                }
                            }
                        }
                        users.isEmpty() -> Box(Modifier.fillMaxWidth().heightIn(min = 130.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp))
                                Text("No reactors are available for this reaction.", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(users, key = { it.id }) { user ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(user.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(user.name.ifBlank { "Telegram user" }, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        user.username?.takeIf { it.isNotBlank() }?.let {
                                            Text("@${it.removePrefix("@")}", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } }
        )
    }

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        summary.split(TelegramReactionText.SEPARATOR).filter { it.isNotBlank() }.forEach { entry ->
            val parts = entry.split('\u001f', limit = 2)
            val labelText = parts.getOrNull(1) ?: entry.substringBefore(" · ")
            val customId = Regex("Custom emoji (\\d+)").find(labelText)?.groupValues?.getOrNull(1)
            val key = parts.firstOrNull().orEmpty().takeIf { parts.size == 2 }
                ?: customId?.let { "custom:$it" }
                ?: if (labelText.startsWith("⭐")) "paid" else "emoji:${labelText.takeWhile { !it.isWhitespace() }}"
            val label = customId?.let { "Custom emoji $it" } ?: labelText.takeWhile { !it.isWhitespace() }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.clickable {
                    selected = key to label
                    loading = true
                    error = null
                    users = emptyList()
                }
            ) {
                Text(labelText, Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}
