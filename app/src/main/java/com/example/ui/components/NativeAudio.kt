package com.example.ui.components

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.GhostMessage
import com.example.service.AudioPlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun NativeAudio(message: GhostMessage, load: suspend (Int) -> String?) {
    val attachment = message.fileAttachment ?: return
    val context = LocalContext.current
    val accountId = LocalGhostAccountId.current
    val scope = rememberCoroutineScope()
    val mediaId = "$accountId:${message.telegramChatId}:${message.telegramMsgId}"
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember(mediaId) { mutableStateOf(false) }
    var error by remember(mediaId) { mutableStateOf<String?>(null) }
    var position by remember(mediaId) { mutableFloatStateOf(0f) }
    var seeking by remember(mediaId) { mutableStateOf(false) }

    DisposableEffect(context) {
        val future = MediaController.Builder(context.applicationContext,
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))).buildAsync()
        var disposed = false
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { revision++ }
        }
        future.addListener({
            if (!disposed) runCatching { future.get() }
                .onSuccess { controller = it; it.addListener(listener); revision++ }
                .onFailure { error = "Couldn't connect to audio playback." }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            controller?.removeListener(listener)
            controller = null
            // Releasing a row's controller never releases the service's player.
            MediaController.releaseFuture(future)
        }
    }
    val player = controller
    val active = remember(player, revision, mediaId) { player?.currentMediaItem?.mediaId == mediaId }
    val playing = active && player?.isPlaying == true
    val buffering = active && player?.playbackState == Player.STATE_BUFFERING
    val duration = if (active) (player?.duration ?: 0L).coerceAtLeast(0).toFloat() else 0f
    val speed = if (active) player?.playbackParameters?.speed ?: 1f else 1f
    val playbackError = if (active && player?.playerError != null) "Couldn't play this audio. Tap play to retry." else null
    LaunchedEffect(player, active, playing, seeking, revision) {
        if (active && !seeking) {
            position = player?.currentPosition?.toFloat() ?: 0f
            while (playing) {
                delay(300)
                position = player?.currentPosition?.toFloat() ?: 0f
            }
        } else if (!active) position = 0f
    }
    fun seek(value: Float) { if (active) player?.seekTo(value.coerceIn(0f, duration).toLong()) }
    fun play() {
        val current = controller ?: return
        if (active && current.playerError == null) {
            if (current.isPlaying || current.playWhenReady) current.pause()
            else {
                if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
                current.play()
            }
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                val path = attachment.localPath?.takeIf { File(it).exists() } ?: load(attachment.fileId)
                if (path == null) { error = "Couldn't load audio. Tap play to retry."; return@launch }
                val extras = Bundle().apply {
                    putString("accountId", accountId)
                    putLong("chatId", message.telegramChatId)
                    putLong("messageId", message.telegramMsgId)
                }
                current.setMediaItem(MediaItem.Builder().setMediaId(mediaId).setUri(Uri.fromFile(File(path)))
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(attachment.fileName.ifBlank { "Audio" })
                        .setArtist(message.sourceTitle).setExtras(extras)
                        .apply { message.sourceAvatarPath?.takeIf { File(it).exists() }?.let { setArtworkUri(Uri.fromFile(File(it))) } }
                        .build()).build())
                current.prepare()
                current.play()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Couldn't play audio. Tap play to retry." }
            finally { loading = false }
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(enabled = player != null && !loading, onClick = { play() }, modifier = Modifier.size(48.dp)) {
                if (loading || buffering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playing) "Pause audio" else "Play audio")
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(attachment.fileName.ifBlank { "Audio" }, style = MaterialTheme.typography.labelLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (active) {
                    Slider(value = position.coerceIn(0f, duration.coerceAtLeast(1f)),
                        enabled = duration > 0 && playbackError == null,
                        onValueChange = { seeking = true; position = it },
                        onValueChangeFinished = { seek(position); seeking = false },
                        valueRange = 0f..duration.coerceAtLeast(1f), modifier = Modifier.height(36.dp).semantics {
                            contentDescription = "Audio position"
                            stateDescription = "${audioTime(position)} of ${audioTime(duration)}"
                        })
                    Text("${audioTime(position)} / ${audioTime(duration)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Text(if (loading) "Downloading audio…" else attachment.fileSizeFormatted,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (error ?: playbackError)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
            if (active) {
                var options by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreVert, "Audio options") }
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                        DropdownMenuItem(text = { Text("Speed: ${speed}x") }, onClick = {
                            player?.setPlaybackSpeed(when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f })
                            options = false
                        })
                        DropdownMenuItem(text = { Text("Back 10 seconds") }, onClick = { player?.seekBack(); options = false })
                        DropdownMenuItem(text = { Text("Forward 10 seconds") }, onClick = { player?.seekForward(); options = false })
                        DropdownMenuItem(text = { Text("Stop playback") }, onClick = {
                            player?.stop(); player?.clearMediaItems(); options = false
                        })
                    }
                }
            }
        }
    }
}

private fun audioTime(ms: Float): String {
    val seconds = (ms / 1000).toLong()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
