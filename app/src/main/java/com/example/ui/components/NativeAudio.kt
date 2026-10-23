package com.example.ui.components

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
internal fun NativeAudio(path: String, title: String) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val manager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val player = remember(path) { MediaPlayer() }
    var ready by remember(path) { mutableStateOf(false) }
    var playing by remember(path) { mutableStateOf(false) }
    var duration by remember(path) { mutableStateOf(0) }
    var position by remember(path) { mutableStateOf(0f) }
    var seeking by remember(path) { mutableStateOf(false) }
    var speed by remember(path) { mutableStateOf(1f) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    fun pause() {
        if (ready) runCatching { player.pause() }
        playing = false
    }
    val focus = remember(player) { AudioManager.OnAudioFocusChangeListener { change ->
        if (change != AudioManager.AUDIOFOCUS_GAIN) pause()
    } }
    fun start() {
        if (!ready || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (manager.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            error = "Audio is in use. Tap play to retry."; return
        }
        runCatching {
            player.start()
            runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }.onFailure { speed = 1f }
        }.onSuccess { playing = true; error = null }.onFailure { error = "Couldn't play this audio."; playing = false }
    }
    fun seek(value: Float) {
        if (ready) runCatching { position = value.coerceIn(0f, duration.toFloat()); player.seekTo(position.toInt()) }
    }
    DisposableEffect(player, owner) {
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        player.setOnPreparedListener { ready = true; duration = it.duration.coerceAtLeast(0); start() }
        player.setOnCompletionListener { playing = false; position = duration.toFloat(); manager.abandonAudioFocus(focus) }
        player.setOnErrorListener { _, _, _ -> ready = false; playing = false; error = "This audio format couldn't be played."; manager.abandonAudioFocus(focus); true }
        runCatching { player.setDataSource(path); player.prepareAsync() }.onFailure { error = "Couldn't open this audio." }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { pause(); manager.abandonAudioFocus(focus) } }
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) { pause(); manager.abandonAudioFocus(focus) }
        } }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(receiver) }
            manager.abandonAudioFocus(focus)
            player.release()
        }
    }
    LaunchedEffect(player, playing, seeking) {
        while (playing && !seeking) { runCatching { position = player.currentPosition.toFloat() }; delay(300) }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                FilledTonalIconButton(enabled = ready, onClick = {
                    if (playing) { pause(); manager.abandonAudioFocus(focus) }
                    else { if (position >= duration && duration > 0) seek(0f); start() }
                }, modifier = Modifier.size(48.dp)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause audio" else "Play audio")
                }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(value = position.coerceIn(0f, duration.coerceAtLeast(1).toFloat()), enabled = ready && duration > 0,
                onValueChange = { seeking = true; position = it },
                onValueChangeFinished = { seek(position); seeking = false },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(), modifier = Modifier.height(36.dp).semantics {
                    contentDescription = "Audio position"
                    stateDescription = "${audioTime(position.toInt())} of ${audioTime(duration)}"
                })
                Text("${audioTime(position.toInt())} / ${audioTime(duration)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                if (!ready && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            var options by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreVert, "Audio options", Modifier.size(18.dp)) }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(enabled = ready, text = { Text("Speed: ${speed}x") }, onClick = {
                        speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                        if (playing) runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }.onFailure { speed = 1f }
                    })
                    DropdownMenuItem(enabled = ready, text = { Text("Back 10 seconds") }, onClick = { seek(position - 10_000); options = false })
                    DropdownMenuItem(enabled = ready, text = { Text("Forward 10 seconds") }, onClick = { seek(position + 10_000); options = false })
                }
            }
        }
    }
}

private fun audioTime(ms: Int) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
