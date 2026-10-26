package com.example.ui.components

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.service.AudioPlaybackService
import kotlinx.coroutines.delay

@Composable
internal fun AudioPlaybackBar(onOpenMusic: () -> Unit) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var duration by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isScreenActive = true
                Lifecycle.Event.ON_STOP -> isScreenActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val future = MediaController.Builder(
            context.applicationContext,
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        ).buildAsync()
        var disposed = false
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                playbackState = player.playbackState
                duration = player.duration.takeIf { it > 0L } ?: 0L
                revision++
            }
        }
        future.addListener({
            if (!disposed) runCatching { future.get() }.onSuccess {
                controller = it
                it.addListener(listener)
                isPlaying = it.isPlaying
                playbackState = it.playbackState
                duration = it.duration.takeIf { value -> value > 0L } ?: 0L
                revision++
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            controller?.removeListener(listener)
            controller = null
            MediaController.releaseFuture(future)
        }
    }

    val player = controller ?: return
    val item = remember(player, revision) { player.currentMediaItem } ?: return
    var position by remember(player, item.mediaId) {
        mutableLongStateOf(player.currentPosition.coerceAtLeast(0L))
    }
    LaunchedEffect(player, item.mediaId, isPlaying, playbackState, isScreenActive) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (!isScreenActive || !isPlaying || playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) break
            delay(250)
        }
    }
    val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Audio"
    val artist = item.mediaMetadata.artist?.toString().orEmpty()

    Surface(
        onClick = onOpenMusic,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(audioBarTime(position) + if (duration > 0) " / ${audioBarTime(duration)}" else "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (artist.isNotBlank()) {
                    Text(artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LinearProgressIndicator(
                    progress = if (duration == 0L) 0f else (position.toFloat() / duration).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "Pause audio" else "Resume audio")
            }
            IconButton(onClick = { player.stop(); player.clearMediaItems() }) {
                Icon(Icons.Default.Close, "Close audio player")
            }
        }
    }
}

private fun audioBarTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
