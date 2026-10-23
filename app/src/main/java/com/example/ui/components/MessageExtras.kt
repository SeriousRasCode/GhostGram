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
import com.example.data.model.UserAccountSession
import com.example.data.remote.TelegramUserClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
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
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, "File", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(attachment.fileName, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
            } }) { Icon(Icons.Default.OpenInNew, "Open file") }
        }
        TextButton(onClick = save, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save to Downloads")
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
internal fun MessageComments(message: GhostMessage, session: UserAccountSession, load: suspend (Int) -> String?, download: (GhostMessage) -> Unit) {
    if (message.commentCount < 0 || message.telegramChatId == 0L) return
    var shown by remember(message.telegramChatId, message.id) { mutableStateOf(false) }
    TextButton(onClick = { shown = true }) {
        Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (message.commentCount == 0) "View discussion" else "${message.commentCount} comments")
    }
    if (shown) CommentsDialog(message, session, load, download) { shown = false }
}

@Composable
private fun CommentsDialog(message: GhostMessage, session: UserAccountSession, load: suspend (Int) -> String?, download: (GhostMessage) -> Unit, dismiss: () -> Unit) {
    val client = TelegramUserClient.getInstance(LocalContext.current, LocalGhostAccountId.current)
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<GhostMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var end by remember { mutableStateOf(false) }
    suspend fun fetch(more: Boolean) {
        if (more && loading) return
        loading = true
        try {
            val page = client.getComments(message.telegramChatId, message.telegramMsgId, if (more) comments.lastOrNull()?.id ?: 0 else 0)
            val merged = (if (more) comments + page else page).distinctBy { it.telegramChatId to it.id }.sortedByDescending { it.id }
            end = page.isEmpty() || (more && merged.size == comments.size)
            comments = merged
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't load comments. Please retry." }
        finally { loading = false }
    }
    LaunchedEffect(message.telegramChatId, message.id) {
        var discussionId: Long? = null
        try {
            val threadChatId = client.execute(TdApi.GetMessageThread(message.telegramChatId, message.telegramMsgId)).chatId
            discussionId = threadChatId
            client.execute(TdApi.OpenChat(threadChatId))
            fetch(false)
            client.updates.collect { update ->
                val changedChat = when (update) {
                    is TdApi.UpdateNewMessage -> update.message.chatId
                    is TdApi.UpdateMessageContent -> update.chatId
                    is TdApi.UpdateMessageInteractionInfo -> update.chatId
                    is TdApi.UpdateDeleteMessages -> update.chatId
                    else -> null
                }
                if (changedChat == threadChatId && !loading) fetch(false)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { loading = false; error = "Couldn't load comments. Please retry." }
        finally {
            discussionId?.let { id -> withContext(NonCancellable) {
                try { client.execute(TdApi.CloseChat(id)) } catch (_: Exception) { }
            } }
        }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Comments", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { scope.launch { fetch(false) } }, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh comments") }
                    IconButton(onClick = dismiss) { Icon(Icons.Default.Close, "Close comments") }
                }
                LazyColumn(Modifier.weight(1f), reverseLayout = true) {
                    items(comments, key = { "${it.telegramChatId}:${it.id}" }) { comment ->
                        MessageItemCard(comment, {}, session, download, load)
                    }
                    item {
                        if (loading) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else if (error != null) TextButton(onClick = { scope.launch { fetch(false) } }) { Text(error!!) }
                        else if (comments.isEmpty()) Text("No comments yet", Modifier.padding(20.dp))
                        else if (!end) TextButton(onClick = { scope.launch { fetch(true) } }) { Text("Load earlier comments") }
                    }
                }
            }
        }
    }
}
