package com.example.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ChatSourceType
import com.example.data.model.FocusSource
import java.util.Date

@Composable
fun SourceListItem(source: FocusSource, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showOptions by remember(source.id) { mutableStateOf(false) }
    val type = when (source.type) {
        ChatSourceType.CHANNEL -> "Channel"
        ChatSourceType.PERSONAL -> "Private chat"
        ChatSourceType.SAVED_MESSAGES -> "Saved messages"
        else -> "Group"
    }
    val timestamp = if (source.lastMessageTime <= 0) "" else if (sameMessageDay(source.lastMessageTime, System.currentTimeMillis()))
        DateFormat.getTimeFormat(context).format(Date(source.lastMessageTime))
        else java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(Date(source.lastMessageTime))
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().heightIn(min = 68.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(42.dp).clearAndSetSemantics {}, shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    if (!source.avatarPath.isNullOrBlank()) AsyncImage(source.avatarPath, null,
                        Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    else Text(source.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f).padding(start = 11.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(source.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(source.lastMessageText?.takeIf { it.isNotBlank() }?.replace('\n', ' ') ?: "Open conversation",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (timestamp.isBlank()) type else "$type · $timestamp",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showOptions = true }, modifier = Modifier.size(48.dp).testTag("delete_source_button")) {
                    Icon(Icons.Default.MoreVert, "Options for ${source.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                    DropdownMenuItem(text = { Text("Open conversation") }, leadingIcon = { Icon(Icons.Default.ChatBubbleOutline, null) },
                        onClick = { showOptions = false; onClick() })
                    DropdownMenuItem(text = { Text("Remove from focus") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                        onClick = { showOptions = false; onDelete() })
                }
            }
        }
    }
}
