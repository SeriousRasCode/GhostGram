package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GhostMessage
import com.example.data.model.UserAccountSession

@Composable
internal fun CommentsSheetContent(
    post: GhostMessage,
    comments: List<GhostMessage>,
    session: UserAccountSession,
    loading: Boolean,
    error: String?,
    endReached: Boolean,
    listState: LazyListState,
    fontSizeScale: Float,
    load: suspend (Int) -> String?,
    download: (GhostMessage) -> Unit,
    onOpenReply: (GhostMessage) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compact = LocalConfiguration.current.screenHeightDp < 500 || LocalDensity.current.fontScale > 1.5f
    Column(modifier.fillMaxWidth().semantics { paneTitle = "Channel discussion" }) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Discussion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() })
                Text(post.sourceTitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Refresh, "Refresh comments")
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, "Close comments")
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Forum, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    if (!compact) Text("Original post", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(post.text.takeIf { it.isNotBlank() } ?: post.fileAttachment?.fileName ?: "Media post",
                        style = MaterialTheme.typography.bodyMedium, maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            when {
                comments.isEmpty() && loading -> Column(Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(32.dp).semantics { contentDescription = "Loading comments" })
                    Text("Loading discussion…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                comments.isEmpty() -> ConversationEmptyState(
                    title = if (error != null) "Couldn't load comments" else "No comments yet",
                    description = error ?: "Replies to this post will appear here.",
                    actionLabel = if (error != null) "Try again" else "Refresh",
                    onAction = onRefresh, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)) {
                    itemsIndexed(comments, key = { _, item -> "${item.telegramChatId}:${item.telegramMsgId}" }) { index, comment ->
                        val older = comments.getOrNull(index + 1)
                        Column {
                            if (older == null || !sameMessageDay(older.timestamp, comment.timestamp)) MessageDateSeparator(comment.timestamp)
                            MessageItemCard(comment, {}, session, download, load, onOpenReply = onOpenReply,
                                fontSizeScale = fontSizeScale, allowBookmark = false,
                                startsGroup = !messagesBelongTogether(older, comment),
                                endsGroup = !messagesBelongTogether(comment, comments.getOrNull(index - 1)))
                        }
                    }
                    item(key = "discussion_history_status") {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (loading) CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = "Loading earlier comments" })
                            else if (error != null) {
                                Text(error, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                TextButton(onClick = onRefresh) { Text("Try again") }
                            } else if (!endReached) TextButton(onClick = onLoadMore, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Load earlier comments")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationEmptyState(title: String, description: String, actionLabel: String, onAction: () -> Unit,
    modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp).widthIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
    }
}
