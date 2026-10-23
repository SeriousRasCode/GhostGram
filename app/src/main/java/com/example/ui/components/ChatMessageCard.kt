package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatSourceType
import com.example.data.model.GhostMessage
import com.example.data.model.UserAccountSession
import java.util.Date

/** Shared message treatment for channels, conversations, and discussion replies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatMessageCard(
    message: GhostMessage,
    session: UserAccountSession,
    onBookmark: ((GhostMessage) -> Unit)?,
    download: (GhostMessage) -> Unit,
    load: suspend (Int) -> String?,
    onOpenReply: ((GhostMessage) -> Unit)?,
    modifier: Modifier = Modifier,
    fontSizeScale: Float = 1f,
    startsGroup: Boolean = true,
    endsGroup: Boolean = true
) {
    val context = LocalContext.current
    val channel = message.chatType == ChatSourceType.CHANNEL
    val mine = !channel && ((message.senderId != 0L && message.senderId == session.userId) ||
        message.senderName == "You" || message.chatType == ChatSourceType.SAVED_MESSAGES)
    val group = message.chatType == ChatSourceType.GROUP
    val scale = fontSizeScale.coerceIn(0.8f, 2.5f)
    val hasSticker = message.stickerFileId != 0 || !message.stickerPath.isNullOrBlank()
    val showText = message.text.isNotBlank() && message.text !in listOf("[Photo]", "[Video]", "[GIF]") &&
        !(hasSticker && message.text.startsWith("Sticker"))
    var options by remember(message.telegramChatId, message.telegramMsgId) { mutableStateOf(false) }
    var summary by remember(message.telegramChatId, message.telegramMsgId) { mutableStateOf(false) }
    val bubbleColor = when {
        mine -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val shape = RoundedCornerShape(
        topStart = if (!channel && !mine && !startsGroup) 8.dp else 20.dp,
        topEnd = if (mine && !startsGroup) 8.dp else 20.dp,
        bottomEnd = if (mine && endsGroup) 6.dp else 20.dp,
        bottomStart = if (!channel && !mine && endsGroup) 6.dp else 20.dp)
    val time = remember(message.timestamp, context) { DateFormat.getTimeFormat(context).format(Date(message.timestamp)) }
    val fullTime = remember(message.timestamp) { java.text.DateFormat.getDateTimeInstance().format(Date(message.timestamp)) }
    val largeText = LocalDensity.current.fontScale * scale > 1.5f

    BoxWithConstraints(modifier.fillMaxWidth().padding(top = if (startsGroup) 10.dp else 2.dp, bottom = 2.dp)) {
        val showAvatar = group && !mine && !largeText && maxWidth >= 340.dp
        val width = if (channel || largeText) maxWidth.coerceAtMost(640.dp)
            else (maxWidth - if (showAvatar) 44.dp else 24.dp).coerceAtMost(520.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom) {
            if (showAvatar) {
                if (endsGroup) SourceAvatar(message, session, 32.dp) else Spacer(Modifier.width(32.dp))
                Spacer(Modifier.width(8.dp))
            }
            Surface(shape = shape, color = bubbleColor,
                border = if (channel) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                modifier = Modifier.widthIn(min = 144.dp, max = width)) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 2.dp)) {
                    if (channel) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SourceAvatar(message, session, 32.dp)
                            Text(message.sourceTitle, Modifier.weight(1f).padding(start = 10.dp),
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    } else if (group && !mine && startsGroup) {
                        Text(message.senderName, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (message.isPinned || message.isUrgent) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            if (message.isPinned) MessageBadge(Icons.Default.PushPin, "Pinned")
                            if (message.isUrgent) MessageBadge(Icons.Default.Bolt, "Priority")
                        }
                    }
                    ReplyContext(message, onOpenReply)
                    if (message.photoFileId != 0 || !message.imageUrl.isNullOrBlank()) {
                        MediaPreview(message.imageUrl ?: "", message.photoFileId, load) { download(message) }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (message.animationMimeType != null) {
                        InlineMedia(message, load)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (showText) RichMessageText(message.text, message.formattedEntities,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = (16 * scale).sp,
                            lineHeight = (24 * scale).sp, letterSpacing = 0.sp))
                    if (hasSticker) InlineSticker(message, load)
                    if (message.animationMimeType == null && message.fileAttachment?.let {
                        it.mimeType.startsWith("audio/") || it.mimeType.startsWith("video/") } == true) {
                        Spacer(Modifier.height(8.dp))
                        InlineMedia(message, load)
                    }
                    DocumentCard(message, load) { download(message) }
                    if (!message.reactions.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        MessageReactions(message.reactions)
                    }
                    FlowRow(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (mine) Text("You", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (message.isBookmarked) Icon(Icons.Default.Bookmark, "Bookmarked", Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            if (channel && !message.views.isNullOrBlank()) {
                                Icon(Icons.Default.Visibility, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(message.views, Modifier.clearAndSetSemantics { contentDescription = "${message.views} views" },
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(time, Modifier.clearAndSetSemantics { contentDescription = fullTime },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { options = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.MoreHoriz, "Message options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                                if (message.text.isNotBlank()) DropdownMenuItem(text = { Text("Copy text") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                            .setPrimaryClip(ClipData.newPlainText("Message", message.text))
                                        options = false
                                    })
                                if (onBookmark != null) DropdownMenuItem(
                                    text = { Text(if (message.isBookmarked) "Remove bookmark" else "Bookmark") },
                                    leadingIcon = { Icon(if (message.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null) },
                                    onClick = { onBookmark(message); options = false })
                                if (message.replyToMessageId != 0L && onOpenReply != null) DropdownMenuItem(
                                    text = { Text("Open original message") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                                    onClick = { options = false; onOpenReply(message) })
                                if (message.fileAttachment != null || message.photoFileId != 0 || !message.imageUrl.isNullOrBlank()) DropdownMenuItem(
                                    text = { Text("Save attachment") }, leadingIcon = { Icon(Icons.Default.Download, null) },
                                    onClick = { options = false; download(message) })
                                if (message.text.isNotBlank()) DropdownMenuItem(text = { Text("Summarise") },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                                    onClick = { options = false; summary = true })
                            }
                        }
                    }
                    MessageComments(message, session, load, download, onOpenReply, fontSizeScale = fontSizeScale)
                }
            }
        }
    }
    if (summary) MessageSummaryDialog(message.text, onDismiss = { summary = false })
}

@Composable
private fun MessageBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun MessageDateSeparator(timestamp: Long) {
    val label = remember(timestamp) { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(Date(timestamp)) }
    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(label, Modifier.padding(horizontal = 14.dp, vertical = 6.dp).semantics { heading() },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
