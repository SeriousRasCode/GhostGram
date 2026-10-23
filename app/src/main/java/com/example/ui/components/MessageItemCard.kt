package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChatSourceType
import com.example.data.model.GhostMessage
import com.example.data.model.UserAccountSession
import com.example.ui.theme.GhostAmber
import com.example.ui.theme.GhostSky
import com.example.ui.theme.GhostTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageItemCard(
    message: GhostMessage,
    onToggleBookmark: (GhostMessage) -> Unit,
    currentUserSession: UserAccountSession,
    onDownloadAttachment: (GhostMessage) -> Unit = {},
    onLoadInlineMedia: suspend (Int) -> String? = { null },
    modifier: Modifier = Modifier,
    isFeedItem: Boolean = false,
    fontSizeScale: Float = 1.0f,
    onOpenReply: ((GhostMessage) -> Unit)? = null,
    onRefreshReactions: suspend (GhostMessage) -> Unit = {},
    startsGroup: Boolean = true,
    endsGroup: Boolean = true,
    allowBookmark: Boolean = true
) {
    val context = LocalContext.current
    var showActions by remember(message.sourceId, message.telegramMsgId) { mutableStateOf(false) }
    var showSummary by remember(message.sourceId, message.telegramMsgId) { mutableStateOf(false) }
    LaunchedEffect(message.sourceId, message.telegramMsgId) {
        if (message.chatType == ChatSourceType.CHANNEL) {
            kotlinx.coroutines.delay(800)
            while (true) {
                onRefreshReactions(message)
                kotlinx.coroutines.delay(60_000)
            }
        }
    }
    if (!isFeedItem) {
        ChatMessageCard(message, currentUserSession, if (allowBookmark) onToggleBookmark else null,
            onDownloadAttachment, onLoadInlineMedia, onOpenReply, modifier, fontSizeScale, startsGroup, endsGroup)
        return
    }
    if (showActions) CompactDialog(onDismissRequest = { showActions = false },
        title = { Text("Message", style = MaterialTheme.typography.titleMedium) },
        text = { TextButton(enabled = message.text.isNotBlank(), onClick = { showActions = false; showSummary = true }) { Text("Summarise") } },
        confirmButton = { TextButton(onClick = { showActions = false }) { Text("Close") } })
    if (showSummary) MessageSummaryDialog(message.text, onDismiss = { showSummary = false })
    val documentAttachment = message.fileAttachment?.let {
        !it.mimeType.startsWith("video/") && !it.mimeType.startsWith("audio/") && message.animationMimeType == null
    } == true
    val hasPhoto = message.photoFileId != 0 || !message.imageUrl.isNullOrBlank()
    val articleLayout = isFeedItem
    val compactArticle = isFeedItem && !hasPhoto && message.text.length < 240
    val hasSticker = message.stickerFileId != 0 || !message.stickerPath.isNullOrBlank()
    val showText = message.text.isNotBlank() && !(hasSticker && message.text.startsWith("Sticker"))
    val isMe = (message.senderId != 0L && message.senderId == currentUserSession.userId) ||
               message.senderName == "You" ||
               message.chatType == ChatSourceType.SAVED_MESSAGES
    
    val alignment = Alignment.CenterHorizontally

    Column(
        modifier = modifier.clickable(onClickLabel = "Message options") { showActions = true }
            .fillMaxWidth()
            .padding(vertical = if (articleLayout && !compactArticle) 6.dp else 3.dp, horizontal = if (isFeedItem) 0.dp else 8.dp),
        horizontalAlignment = alignment
    ) {
        if (articleLayout) {
            // NEWSPAPER STYLE: Article Layout
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                color = if (MaterialTheme.colorScheme.surface == Color.White) com.example.ui.theme.GhostNewsprint else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(if (compactArticle) 12.dp else 16.dp)) {
                    // Article Header: Source & Date (Newspaper Style)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.sourceTitle.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = (11 * fontSizeScale).sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatRelativeTime(message.timestamp).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = (10 * fontSizeScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }

                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    // Article Content
                    ReplyContext(message, onOpenReply)
                    if (message.photoFileId != 0 || !message.imageUrl.isNullOrBlank()) {
                        MediaPreview(
                            url = message.imageUrl ?: "", fileId = message.photoFileId, load = onLoadInlineMedia,
                            onDownload = { onDownloadAttachment(message) }
                        )
                        Text(
                            text = "PHOTO / ${message.sourceTitle}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = (9 * fontSizeScale).sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                    }

                    if (message.animationMimeType != null) {
                        InlineMedia(message, onLoadInlineMedia)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (showText && message.text !in listOf("[Photo]", "[Video]", "[GIF]")) {
                        val firstBreak = message.text.indexOf('\n')
                        val headlineEnd = if (firstBreak in 1..140) firstBreak else 0
                        if (headlineEnd > 0) {
                            RichMessageText(message.text, message.formattedEntities, onTextClick = { showActions = true },
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    fontWeight = FontWeight.Bold, fontSize = ((if (compactArticle) 19 else 24) * fontSizeScale).sp,
                                    lineHeight = ((if (compactArticle) 25 else 30) * fontSizeScale).sp), displayEnd = headlineEnd)
                            Spacer(Modifier.height(10.dp))
                        }
                        RichMessageText(message.text, message.formattedEntities, onTextClick = { showActions = true },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                fontSize = ((if (compactArticle) 15 else 17) * fontSizeScale).sp,
                                lineHeight = ((if (compactArticle) 23 else 27) * fontSizeScale).sp),
                            displayStart = if (headlineEnd > 0) headlineEnd + 1 else 0)
                    }
                    // Multimedia elements
                    if (hasSticker) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { InlineSticker(message, onLoadInlineMedia) }
                    }

                    if (message.animationMimeType == null && message.fileAttachment?.let {
                        it.mimeType.startsWith("video/") || it.mimeType.startsWith("audio/")
                    } == true) InlineMedia(message, onLoadInlineMedia)
                    DocumentCard(message, onLoadInlineMedia) { onDownloadAttachment(message) }
                    MessageReactions(message.reactions)
                    // Article Footer: Byline, Reactions, Views
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(top = if (compactArticle) 8.dp else 16.dp, bottom = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (message.chatType != ChatSourceType.CHANNEL && !isMe) {
                                Text(
                                    text = "BY ${message.senderName.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                if (message.chatType == ChatSourceType.CHANNEL && !message.views.isNullOrBlank()) {
                                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${message.views} VIEWS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Accessibility: Large touch targets for interactions
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("Message", message.text))
                            }) { Icon(Icons.Default.ContentCopy, "Copy article", Modifier.size(18.dp)) }
                            IconButton(
                                onClick = { onToggleBookmark(message) },
                                modifier = Modifier.size(48.dp) // Large touch target
                            ) {
                                Icon(
                                    imageVector = if (message.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (message.isBookmarked) "Remove Bookmark" else "Save to Bookmarks",
                                    tint = if (message.isBookmarked) GhostTeal else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            if (message.fileAttachment != null && !documentAttachment) {
                                IconButton(
                                    onClick = { onDownloadAttachment(message) },
                                    modifier = Modifier.size(48.dp) // Large touch target
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Attachment",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        MessageComments(message, currentUserSession, onLoadInlineMedia, onDownloadAttachment, onOpenReply, fontSizeScale)
    }
}

@Composable
internal fun ReplyContext(message: GhostMessage, onOpenReply: ((GhostMessage) -> Unit)?) {
    val preview = message.replyToText ?: return
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 48.dp)
            .clickable(enabled = message.replyToMessageId != 0L && onOpenReply != null,
                onClickLabel = "Open original message") { onOpenReply?.invoke(message) }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                text = "Reply to ${message.replyToSenderName ?: "message"}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SourceAvatar(
    message: GhostMessage, 
    currentUserSession: UserAccountSession,
    size: androidx.compose.ui.unit.Dp
) {
    val isMe = (message.senderId != 0L && message.senderId == currentUserSession.userId) ||
               message.senderName == "You" ||
               message.chatType == ChatSourceType.SAVED_MESSAGES
    val avatarPath = if (isMe) currentUserSession.profilePicPath else message.sourceAvatarPath
    
    Surface(
        modifier = Modifier.size(size).clearAndSetSemantics {},
        shape = CircleShape,
        color = when(message.chatType) {
            ChatSourceType.CHANNEL -> GhostSky.copy(alpha = 0.2f)
            ChatSourceType.PERSONAL -> GhostTeal.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!avatarPath.isNullOrBlank()) {
                AsyncImage(
                    model = avatarPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val displayName = if (isMe) (currentUserSession.userName.ifBlank { "You" })
                    else if (message.chatType == ChatSourceType.CHANNEL) message.sourceTitle else message.senderName
                val avatarChar = displayName.firstOrNull()?.uppercaseChar() ?: 'G'
                Text(
                    text = avatarChar.toString(),
                    style = if (size > 30.dp) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun MediaPreview(url: String, fileId: Int, load: suspend (Int) -> String?, onDownload: () -> Unit) {
    var actualUrl by remember(url, fileId) { mutableStateOf(url.takeIf { it.startsWith("http") || (it.isNotBlank() && java.io.File(it).exists()) }) }
    var retry by remember(fileId) { mutableStateOf(0) }
    var loading by remember(fileId) { mutableStateOf(false) }
    LaunchedEffect(fileId, retry) {
        if (actualUrl == null && fileId != 0) {
            loading = true
            try { actualUrl = load(fileId) } finally { loading = false }
        }
    }
    val context = LocalContext.current
    var showFullScreen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (actualUrl == null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().clickable(enabled = !loading, onClickLabel = "Retry photo") { retry++ },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (loading) "Loading photo..." else "Tap to retry photo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(actualUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Attachment preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Open photo") { showFullScreen = true }
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download picture",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showFullScreen) {
        Dialog(
            onDismissRequest = { showFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = actualUrl,
                        contentDescription = "Full Screen Media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { showFullScreen = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, "Close photo", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val diffSec = diffMs / 1000
    val diffMin = diffSec / 60
    val diffHour = diffMin / 60

    return when {
        diffMin < 1 -> "just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffHour < 24 -> "${diffHour}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
