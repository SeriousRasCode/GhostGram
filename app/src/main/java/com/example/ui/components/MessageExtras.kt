package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.model.GhostMessage
import com.example.data.model.ChatSourceType
import com.example.data.model.UserAccountSession
import com.example.data.remote.TelegramUserClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi
import java.io.File

@Composable
internal fun DocumentCard(message: GhostMessage, load: suspend (Int) -> String?, save: () -> Unit) {
    val attachment = message.fileAttachment ?: return
    if (attachment.mimeType.startsWith("audio/") || attachment.mimeType.startsWith("video/") || message.animationMimeType != null) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember(attachment.fileId, attachment.localPath) { mutableStateOf(attachment.localPath) }
    var loading by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, "File", Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(attachment.fileName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${attachment.fileSizeFormatted} · ${attachment.fileName.substringAfterLast('.', "FILE").uppercase()}", style = MaterialTheme.typography.labelSmall)
            }
            if (loading) CircularProgressIndicator(Modifier.size(24.dp))
            else IconButton(onClick = { scope.launch {
                loading = true
                try {
                    val local = path?.takeIf { File(it).exists() } ?: load(attachment.fileId)
                    path = local
                    if (local != null) openDocument(context, local, attachment.mimeType)
                    else Toast.makeText(context, "Couldn't load file. Please retry.", Toast.LENGTH_SHORT).show()
                } finally { loading = false }
            } }) { Icon(Icons.Default.OpenInNew, "Open file", Modifier.size(20.dp)) }
            IconButton(onClick = save) { Icon(Icons.Default.Download, "Save to Downloads", Modifier.size(20.dp)) }
        }
    }
}

private fun openDocument(context: Context, path: String, mime: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (_: Exception) { Toast.makeText(context, "No app is available to open this file.", Toast.LENGTH_SHORT).show() }
}

@Composable
internal fun MessageComments(message: GhostMessage, session: UserAccountSession, load: suspend (Int) -> String?, download: (GhostMessage) -> Unit,
    onOpenReply: ((GhostMessage) -> Unit)? = null, fontSizeScale: Float = 1f) {
    if (message.chatType != ChatSourceType.CHANNEL || message.commentCount < 0 || message.telegramChatId == 0L) return
    var shown by remember(message.telegramChatId, message.id) { mutableStateOf(false) }
    Surface(onClick = { shown = true }, color = MaterialTheme.colorScheme.primaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(when (message.commentCount) { 0 -> "View discussion"; 1 -> "1 comment"; else -> "${message.commentCount} comments" },
                Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    if (shown) CommentsDialog(message, session, load, download, onOpenReply, fontSizeScale) { shown = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsDialog(message: GhostMessage, session: UserAccountSession, load: suspend (Int) -> String?, download: (GhostMessage) -> Unit,
    onOpenReply: ((GhostMessage) -> Unit)?, fontSizeScale: Float, dismiss: () -> Unit) {
    val client = TelegramUserClient.getInstance(LocalContext.current, LocalGhostAccountId.current)
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var discussionChatId by remember { mutableStateOf(0L) }
    var replyJumpId by remember { mutableStateOf(0L) }
    var comments by remember { mutableStateOf<List<GhostMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var end by remember { mutableStateOf(false) }
    val fetchMutex = remember { Mutex() }
    var retryVersion by remember { mutableStateOf(0) }
    suspend fun fetch(more: Boolean) = fetchMutex.withLock {
        loading = true
        try {
            val page = client.getComments(message.telegramChatId, message.telegramMsgId, if (more) comments.lastOrNull()?.id ?: 0 else 0)
            val merged = mergeDiscussionMessages(comments, page)
            if (more || comments.isEmpty()) end = page.isEmpty() || (more && merged.size == comments.size)
            comments = merged
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't load comments. Please retry." }
        finally { loading = false }
    }
    fun openReply(reply: GhostMessage) {
        val chatId = reply.replyToChatId.takeIf { it != 0L } ?: reply.telegramChatId
        if (chatId != discussionChatId) {
            dismiss()
            onOpenReply?.invoke(reply)
            return
        }
        scope.launch {
            val existing = comments.indexOfFirst { it.id == reply.replyToMessageId }
            if (existing >= 0) listState.scrollToItem(existing)
            else {
                loading = true
                try {
                    val target = client.execute(TdApi.GetMessage(chatId, reply.replyToMessageId))
                    val history = client.execute(TdApi.GetMessageThreadHistory(message.telegramChatId, message.id, reply.replyToMessageId, -25, 50))
                    comments = (history.messages.filterNotNull() + target).distinctBy { it.id }.sortedByDescending { it.id }
                        .map { client.mapToGhostMessage(it, discussionChatId, "Discussion", session.userId) }
                    end = false
                    error = null
                    replyJumpId = reply.replyToMessageId
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { error = "The original message is no longer accessible" }
                finally { loading = false }
            }
        }
    }
    LaunchedEffect(replyJumpId, comments.size) {
        if (replyJumpId != 0L) {
            val index = comments.indexOfFirst { it.id == replyJumpId }
            if (index >= 0) { listState.scrollToItem(index); replyJumpId = 0L }
        }
    }
    LaunchedEffect(message.telegramChatId, message.id, retryVersion) {
        var discussionId: Long? = null
        try {
            val threadChatId = client.execute(TdApi.GetMessageThread(message.telegramChatId, message.telegramMsgId)).chatId
            discussionChatId = threadChatId
            discussionId = threadChatId
            client.setOpenChats("discussion:${message.telegramChatId}:${message.id}", setOf(threadChatId))
            fetch(false)
            client.updates.collect { update ->
                val changedChat = when (update) {
                    is TdApi.UpdateNewMessage -> update.message.chatId
                    is TdApi.UpdateMessageContent -> update.chatId
                    is TdApi.UpdateMessageInteractionInfo -> update.chatId
                    is TdApi.UpdateDeleteMessages -> update.chatId
                    else -> null
                }
                if (changedChat == threadChatId) {
                    if (update is TdApi.UpdateDeleteMessages && update.isPermanent) {
                        comments = comments.filterNot { it.telegramMsgId in update.messageIds }
                    }
                    fetch(false)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { loading = false; error = "Couldn't load comments. Please retry." }
        finally {
            discussionId?.let { id -> withContext(NonCancellable) {
                try { client.setOpenChats("discussion:${message.telegramChatId}:${message.id}", emptySet()) } catch (_: Exception) { }
            } }
        }
    }
    ModalBottomSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        CommentsSheetContent(message, comments, session, loading, error, end, listState, fontSizeScale,
            load, download, ::openReply,
            onRefresh = { loading = true; error = null; retryVersion++ },
            onLoadMore = { scope.launch { fetch(true) } },
            onDismiss = dismiss,
            modifier = Modifier.fillMaxHeight(0.92f))
    }
}
