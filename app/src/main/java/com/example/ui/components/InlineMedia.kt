package com.example.ui.components

import android.util.Base64
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.AudioAttributes
import android.content.Context
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
internal fun InlineMedia(message: GhostMessage, load: suspend (Int) -> String?, autoLoadMedia: Boolean = true) {
    val attachment = message.fileAttachment ?: return
    if (attachment.mimeType.startsWith("audio/")) {
        NativeAudio(message, load)
        return
    }
    val scope = rememberCoroutineScope()
    var path by remember(attachment.fileId, attachment.localPath) { mutableStateOf(attachment.localPath?.takeIf { File(it).exists() }) }
    var thumbnailPath by remember(message.animationThumbnailFileId, message.animationThumbnailPath) {
        mutableStateOf(message.animationThumbnailPath?.takeIf { File(it).exists() })
    }
    var loading by remember { mutableStateOf(false) }
    var thumbnailLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }
    var manuallyRequested by remember(attachment.fileId) { mutableStateOf(false) }
    val isAnimation = message.animationMimeType != null
    val thumbnailBytes = remember(message.animationThumbnailData) {
        message.animationThumbnailData?.let { encoded ->
            runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
        }
    }
    val local = path
    suspend fun loadAnimation() {
        if (loading) return
        requested = true
        loading = true
        error = false
        try {
            path = load(attachment.fileId)
            error = path == null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(message.animationThumbnailFileId, message.animationThumbnailMimeType) {
        if (isAnimation && thumbnailPath == null && message.animationThumbnailFileId != 0 &&
            message.animationThumbnailMimeType != "video/mp4") {
            thumbnailLoading = true
            try { thumbnailPath = load(message.animationThumbnailFileId) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { }
            finally { thumbnailLoading = false }
        }
    }
    LaunchedEffect(attachment.fileId, isAnimation, autoLoadMedia) {
        if (isAnimation && autoLoadMedia && path == null) loadAnimation()
    }
    Column(Modifier.fillMaxWidth()) {
        if (isAnimation) {
            val shouldPlay = autoLoadMedia || manuallyRequested
            if (shouldPlay && local != null) {
                if (attachment.mimeType == "image/gif") {
                    AsyncImage(local, "Animated GIF", Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        contentScale = ContentScale.Fit)
                } else {
                    NativeVideo(local, loop = true)
                }
            } else if (!shouldPlay) {
                Surface(shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().height(168.dp)) {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable(onClickLabel = "Load and play GIF", role = Role.Button) {
                            manuallyRequested = true
                            if (path == null) scope.launch { loadAnimation() }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            thumbnailPath != null && message.animationThumbnailMimeType != "video/mp4" ->
                                AsyncImage(thumbnailPath, "GIF preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            thumbnailBytes != null ->
                                AsyncImage(thumbnailBytes, "GIF preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            thumbnailLoading -> CircularProgressIndicator(Modifier.size(28.dp))
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(38.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("GIF", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)) {
                            Icon(Icons.Default.PlayArrow, "Load and play GIF", Modifier.padding(8.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Surface(shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().height(168.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator()
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { manuallyRequested = true; scope.launch { loadAnimation() } }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.PlayArrow, "Retry animation load", modifier = Modifier.size(28.dp))
                            }
                            Text(if (error) "Animation couldn't load. Tap to retry." else "Loading animation…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
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
                            Text(attachment.fileName.ifBlank { "Video" },
                                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (loading) "Loading..." else attachment.fileSizeFormatted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (error) Text("Couldn't load media. Tap to retry.", color = MaterialTheme.colorScheme.error)
            if (local != null && requested && attachment.mimeType.startsWith("video/")) {
                NativeVideo(local, loop = false)
            }
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
            if (!loop) setMediaController(MediaController(context).also { it.setAnchorView(this) })
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
