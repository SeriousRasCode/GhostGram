package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.VideoView
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
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
    fontSizeScale: Float = 1.0f
) {
    val context = LocalContext.current
    val isMe = message.senderId == currentUserSession.userId || 
               message.senderName == "You" ||
               message.chatType == ChatSourceType.SAVED_MESSAGES
    
    val bubbleColor = if (isFeedItem) {
        MaterialTheme.colorScheme.surface
    } else if (isMe) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    }

    val alignment = if (isFeedItem) Alignment.CenterHorizontally else (if (isMe) Alignment.End else Alignment.Start)
    
    val bubbleShape = if (isFeedItem) {
        RoundedCornerShape(16.dp)
    } else if (isMe) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (isFeedItem) 10.dp else 4.dp, horizontal = if (isFeedItem) 0.dp else 8.dp),
        horizontalAlignment = alignment
    ) {
        if (isFeedItem) {
            // NEWSPAPER STYLE: Article Layout
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                color = if (MaterialTheme.colorScheme.surface == Color.White) com.example.ui.theme.GhostNewsprint else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Article Header: Source & Date (Newspaper Style)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.sourceTitle.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = (12 * fontSizeScale).sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
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
                    ReplyContext(message)
                    if (!message.imageUrl.isNullOrBlank()) {
                        MediaPreview(
                            url = message.imageUrl,
                            onDownload = { onDownloadAttachment(message) }
                        )
                        Text(
                            text = "PHOTO: ${message.sourceTitle} / Stealth Relay",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = (9 * fontSizeScale).sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                    }

                    if (message.animationMimeType != null) {
                        InlineAnimation(message, onLoadInlineMedia)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    val textLines = message.text.split("\n")
                    val headline = textLines.firstOrNull() ?: ""
                    val body = if (textLines.size > 1) textLines.drop(1).joinToString("\n") else ""

                    if (headline.isNotBlank() && message.text !in listOf("[Photo]", "[GIF]")) {
                        // Professional Headline
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = (20 * fontSizeScale).sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                lineHeight = (26 * fontSizeScale).sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (body.isNotBlank()) {
                        // Professional Body with "Drop Cap" for the first letter
                        val firstLetter = body.take(1)
                        val remainingBody = body.drop(1)
                        
                        Row {
                            Text(
                                text = firstLetter,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = (38 * fontSizeScale).sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                            )
                            Text(
                                text = remainingBody,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (15 * fontSizeScale).sp,
                                    lineHeight = (22 * fontSizeScale).sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    } else if (headline.isBlank() && message.text.isNotBlank() && !message.text.equals("[Photo]", true)) {
                        val text = message.text
                        val firstLetter = text.take(1)
                        val remainingText = text.drop(1)
                        Row {
                            Text(
                                text = firstLetter,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = (38 * fontSizeScale).sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                            )
                            Text(
                                text = remainingText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (15 * fontSizeScale).sp,
                                    lineHeight = (22 * fontSizeScale).sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Multimedia elements
                    if (!message.stickerPath.isNullOrBlank()) {
                        AsyncImage(
                            model = message.stickerPath,
                            contentDescription = "Sticker",
                            modifier = Modifier.size(160.dp).align(Alignment.CenterHorizontally).padding(vertical = 8.dp)
                        )
                    }

                    if (message.animationMimeType == null && (!message.videoPath.isNullOrBlank() || message.fileAttachment?.mimeType?.startsWith("video") == true)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        VideoDownloadPlaceholder(
                            attachment = message.fileAttachment ?: com.example.data.model.FileAttachment("Video", "", "video/mp4"),
                            onDownload = { onDownloadAttachment(message) }
                        )
                    }

                    if (!message.audioPath.isNullOrBlank() || !message.voicePath.isNullOrBlank() || message.fileAttachment?.mimeType?.startsWith("audio") == true) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AudioDownloadPlaceholder(
                            attachment = message.fileAttachment,
                            onDownload = { onDownloadAttachment(message) }
                        )
                    }

                    // Article Footer: Byline, Reactions, Views
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            if (message.chatType != ChatSourceType.CHANNEL && !isMe) {
                                Text(
                                    text = "BY ${message.senderName.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                if (!message.reactions.isNullOrBlank()) {
                                    Text(
                                        text = message.reactions,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        modifier = Modifier
                                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                
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
                            
                            if (message.fileAttachment != null) {
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
        } else {
            // CHAT STYLE (Bubbles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (!isMe) {
                    SourceAvatar(message, currentUserSession, size = 32.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (!isMe) {
                        Text(
                            text = if (message.chatType == ChatSourceType.CHANNEL) message.sourceTitle else message.senderName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    Surface(
                        shape = bubbleShape,
                        color = bubbleColor,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .border(
                                width = 0.5.dp, 
                                color = if (message.isUrgent) GhostAmber.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                shape = bubbleShape
                            )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            ReplyContext(message)
                            // Urgent/Pinned Badges
                            if (message.isUrgent || message.isPinned) {
                                Row(
                                    modifier = Modifier.padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (message.isUrgent) {
                                        Surface(color = GhostAmber, shape = RoundedCornerShape(4.dp)) {
                                            Text("⚡ RADAR", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Black), color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                    if (message.isPinned) {
                                        Icon(Icons.Default.PushPin, null, tint = GhostSky, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }

                            if (!message.imageUrl.isNullOrBlank()) {
                                MediaPreview(url = message.imageUrl, onDownload = { onDownloadAttachment(message) })
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (message.animationMimeType != null) {
                                InlineAnimation(message, onLoadInlineMedia)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            val isMediaPlaceholder = message.text.equals("[Photo]", true) ||
                                message.text.equals("[Video]", true) || message.text.equals("[GIF]", true)
                            if (message.text.isNotBlank() && (!isMediaPlaceholder ||
                                (message.imageUrl.isNullOrBlank() && message.animationMimeType == null))) {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeScale).sp,
                                        lineHeight = (20 * fontSizeScale).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (!message.stickerPath.isNullOrBlank()) {
                                AsyncImage(model = message.stickerPath, contentDescription = "Sticker", modifier = Modifier.size(140.dp).align(Alignment.CenterHorizontally))
                            }

                            if (message.animationMimeType == null && (!message.videoPath.isNullOrBlank() || message.fileAttachment?.mimeType?.startsWith("video") == true)) {
                                Spacer(modifier = Modifier.height(6.dp))
                                VideoDownloadPlaceholder(attachment = message.fileAttachment ?: com.example.data.model.FileAttachment("Video", "", "video/mp4"), onDownload = { onDownloadAttachment(message) })
                            }

                            if (!message.audioPath.isNullOrBlank() || !message.voicePath.isNullOrBlank() || message.fileAttachment?.mimeType?.startsWith("audio") == true) {
                                Spacer(modifier = Modifier.height(6.dp))
                                AudioDownloadPlaceholder(attachment = message.fileAttachment, onDownload = { onDownloadAttachment(message) })
                            }

                            Row(modifier = Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (message.chatType == ChatSourceType.CHANNEL && !message.views.isNullOrBlank()) {
                                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(text = message.views, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(text = formatRelativeTime(message.timestamp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                if (isMe) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(11.dp), tint = GhostTeal)
                                }
                            }

                            if (!message.reactions.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = message.reactions, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }
                }

                if (isMe) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SourceAvatar(message, currentUserSession, size = 32.dp)
                }
            }
        }

        // Actions (Small icon actions below bubble/card)
        Row(
            modifier = Modifier
                .align(alignment)
                .padding(top = 2.dp, start = if (isFeedItem) 12.dp else 4.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = if (message.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = if (message.isBookmarked) GhostTeal else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp).clickable { onToggleBookmark(message) }
            )
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp).clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Message", message.text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )
            if (message.fileAttachment != null) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp).clickable { onDownloadAttachment(message) }
                )
            }
        }
    }
}

@Composable
private fun ReplyContext(message: GhostMessage) {
    val preview = message.replyToText ?: return
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
private fun InlineAnimation(
    message: GhostMessage,
    onLoadInlineMedia: suspend (Int) -> String?
) {
    val attachment = message.fileAttachment ?: return
    val context = LocalContext.current
    var localPath by remember(message.telegramMsgId) {
        mutableStateOf(attachment.localPath?.takeIf { java.io.File(it).exists() })
    }
    var isLoading by remember(message.telegramMsgId) { mutableStateOf(false) }
    var retry by remember(message.telegramMsgId) { mutableStateOf(0) }

    LaunchedEffect(attachment.fileId, retry) {
        if (localPath == null && attachment.fileId != 0) {
            isLoading = true
            localPath = onLoadInlineMedia(attachment.fileId)?.takeIf { java.io.File(it).exists() }
            isLoading = false
        }
    }

    val path = localPath
    if (path != null) {
        if (message.animationMimeType == "image/gif") {
            AsyncImage(
                model = path,
                contentDescription = "Animated GIF",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp)
            )
        } else {
            val videoView = remember(context, message.telegramMsgId) {
                VideoView(context).apply {
                    setOnPreparedListener { player ->
                        player.isLooping = true
                        player.setVolume(0f, 0f)
                        start()
                    }
                    setOnClickListener { if (isPlaying) pause() else start() }
                }
            }
            DisposableEffect(videoView) { onDispose { videoView.stopPlayback() } }
            AndroidView(
                factory = { videoView },
                update = { view ->
                    if (view.tag != path) {
                        view.tag = path
                        view.setVideoPath(path)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        }
    } else if (isLoading) {
        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    } else {
        TextButton(onClick = { retry++ }) { Text("Tap to load animation") }
    }
}

@Composable
private fun SourceAvatar(
    message: GhostMessage, 
    currentUserSession: UserAccountSession,
    size: androidx.compose.ui.unit.Dp
) {
    val isMe = message.senderId == currentUserSession.userId || 
               message.senderName == "You" ||
               message.chatType == ChatSourceType.SAVED_MESSAGES
    val avatarPath = if (isMe) currentUserSession.profilePicPath else message.sourceAvatarPath
    
    Surface(
        modifier = Modifier.size(size),
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
                val displayName = if (isMe) (currentUserSession.userName.ifBlank { "You" }) else message.sourceTitle
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
private fun MediaPreview(url: String, onDownload: () -> Unit) {
    var isImageLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showFullScreen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (!isImageLoaded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().clickable { isImageLoaded = true },
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
                        "Click to load photo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = "Attachment preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 400.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showFullScreen = true }
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
                    modifier = Modifier.size(36.dp)
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
                        model = url,
                        contentDescription = "Full Screen Media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { showFullScreen = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoDownloadPlaceholder(attachment: com.example.data.model.FileAttachment, onDownload: () -> Unit) {
    val context = LocalContext.current
    val isDownloaded = !attachment.localPath.isNullOrBlank()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { 
                if (isDownloaded) openFile(context, attachment)
                else onDownload() 
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isDownloaded) Icons.Default.PlayCircle else Icons.Default.Download, 
                null, 
                tint = MaterialTheme.colorScheme.primary, 
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Video (${attachment.fileSizeFormatted})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(
                if (isDownloaded) "Click to view" else "Click to verify & download", 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AudioDownloadPlaceholder(attachment: com.example.data.model.FileAttachment?, onDownload: () -> Unit) {
    val context = LocalContext.current
    val isDownloaded = !attachment?.localPath.isNullOrBlank()
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().clickable { 
            if (isDownloaded && attachment != null) openFile(context, attachment)
            else onDownload() 
        }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDownloaded) Icons.Default.PlayArrow else Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = attachment?.fileName ?: "Audio Message", 
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), 
                    maxLines = 1
                )
                Text(
                    text = if (isDownloaded) "${attachment?.fileSizeFormatted} • Click to listen" else "${attachment?.fileSizeFormatted ?: "Unknown size"} • Click to download", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun openFile(context: Context, attachment: com.example.data.model.FileAttachment) {
    val localPath = attachment.localPath ?: return
    try {
        val file = java.io.File(localPath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found on disk", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
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
