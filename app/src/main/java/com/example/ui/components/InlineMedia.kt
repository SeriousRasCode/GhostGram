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
    if (attachment.mimeType.startsWith("audio/")) {
        NativeAudio(message, load)
        return
    }
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
