package com.example.ui.components

import android.media.MediaPlayer
import android.media.AudioManager
import android.media.AudioAttributes
import android.content.Context
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.model.GhostMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun InlineMedia(message: GhostMessage, load: suspend (Int) -> String?) {
    val attachment = message.fileAttachment ?: return
    val scope = rememberCoroutineScope()
    var path by remember(attachment.fileId, attachment.localPath) { mutableStateOf(attachment.localPath?.takeIf { File(it).exists() }) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }
    val isAudio = attachment.mimeType.startsWith("audio/")
    val local = path
    Column(Modifier.fillMaxWidth()) {
        if (local == null || !requested) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !loading, onClick = {
                        requested = true
                        if (path == null) scope.launch {
                            loading = true
                            try { path = load(attachment.fileId); error = path == null } finally { loading = false }
                        }
                    }) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PlayArrow, "Play media")
                    }
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(attachment.fileName.ifBlank { if (isAudio) "Audio" else "Video" },
                            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (loading) "Loading..." else attachment.fileSizeFormatted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (error) Text("Couldn't load media. Tap to retry.", color = MaterialTheme.colorScheme.error)
        if (local != null && requested) when {
            attachment.mimeType == "image/gif" -> AsyncImage(local, "Animation", Modifier.fillMaxWidth().heightIn(max = 240.dp))
            attachment.mimeType.startsWith("video/") -> NativeVideo(local, message.animationMimeType != null)
            else -> NativeAudio(local, attachment.fileName.ifBlank { "Audio" })
        }
    }
}

@Composable
private fun NativeVideo(path: String, loop: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var error by remember(path) { mutableStateOf(false) }
    val video = remember(context, path) {
        VideoView(context).apply {
            setMediaController(MediaController(context).also { it.setAnchorView(this) })
            setOnPreparedListener { it.isLooping = loop; start() }
            setOnErrorListener { _, _, _ -> error = true; true }
            setVideoPath(path)
        }
    }
    DisposableEffect(video, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) video.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); video.stopPlayback() }
    }
    if (error) Text("This video couldn't be played.", color = MaterialTheme.colorScheme.error)
    else AndroidView(factory = { video }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
}

@Composable
private fun NativeAudio(path: String, title: String) {
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var ready by remember(path) { mutableStateOf(false) }
    var playing by remember(path) { mutableStateOf(false) }
    var position by remember(path) { mutableStateOf(0f) }
    var duration by remember(path) { mutableStateOf(0) }
    var error by remember(path) { mutableStateOf(false) }
    val player = remember(path) { MediaPlayer() }
    val focusListener = remember(player) { AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS && ready) { player.pause(); playing = false }
    } }
    DisposableEffect(player, owner) {
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        player.setOnPreparedListener {
            ready = true; duration = it.duration
            if (audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                it.start(); playing = true
            }
        }
        player.setOnCompletionListener { playing = false; position = 0f; it.seekTo(0); audioManager.abandonAudioFocus(focusListener) }
        player.setOnErrorListener { _, _, _ -> error = true; ready = false; playing = false; true }
        try { player.setDataSource(path); player.prepareAsync() } catch (_: Exception) { error = true }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && ready) { player.pause(); playing = false; audioManager.abandonAudioFocus(focusListener) }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); audioManager.abandonAudioFocus(focusListener); player.release() }
    }
    LaunchedEffect(player, playing) {
        while (playing) { position = player.currentPosition.toFloat(); delay(250) }
    }
    if (error) Text("This audio couldn't be played.", color = MaterialTheme.colorScheme.error)
    else Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = ready, onClick = {
            if (playing) { player.pause(); playing = false; audioManager.abandonAudioFocus(focusListener) }
            else if (audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                player.start(); playing = true
            }
        }) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause audio" else "Play audio")
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(value = position.coerceIn(0f, duration.coerceAtLeast(1).toFloat()), onValueChange = { position = it; player.seekTo(it.toInt()) },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = ready)
            Text("${mediaTime(position.toInt())} / ${mediaTime(duration)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun mediaTime(ms: Int) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
