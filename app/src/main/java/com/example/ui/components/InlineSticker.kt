package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.data.model.GhostMessage
import com.example.util.StickerFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun InlineSticker(message: GhostMessage, load: suspend (Int) -> String?) {
    val owner = LocalLifecycleOwner.current
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var path by remember(message.stickerFileId, message.stickerPath) { mutableStateOf(message.stickerPath?.takeIf { File(it).exists() }) }
    var thumbnail by remember(message.stickerThumbnailPath) { mutableStateOf(message.stickerThumbnailPath) }
    var format by remember(message.stickerFileId) { mutableStateOf<String?>(null) }
    var json by remember(message.stickerFileId) { mutableStateOf<String?>(null) }
    var attempt by remember(message.stickerFileId) { mutableStateOf(0) }
    var loading by remember(message.stickerFileId) { mutableStateOf(true) }
    var failed by remember(message.stickerFileId) { mutableStateOf(false) }
    LaunchedEffect(message.stickerFileId, message.stickerPath, attempt) {
        loading = true; failed = false
        try {
            val downloaded = path?.takeIf { File(it).exists() && File(it).length() > 0 }
                ?: message.stickerFileId.takeIf { it != 0 }?.let { load(it) }
            path = downloaded
            if (downloaded != null) {
                format = withContext(Dispatchers.IO) { StickerFiles.format(downloaded, message.stickerFormat) }
                if (format == "tgs") json = withContext(Dispatchers.IO) { StickerFiles.tgsJson(downloaded) }
            } else failed = true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(message.stickerThumbnailFileId) {
        if (thumbnail.isNullOrBlank() && message.stickerThumbnailFileId != 0) thumbnail = load(message.stickerThumbnailFileId)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            val local = path
            if (!loading && !failed && local != null) {
                when (format) {
                    "tgs" -> json?.let { AnimatedTgsSticker(it, active) { failed = true } }
                    "webm" -> key(local, attempt) { VideoSticker(local, active) { failed = true } }
                    else -> AsyncImage(local, message.stickerEmoji ?: "Sticker", Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                        onError = { failed = true })
                }
            } else {
                if (!thumbnail.isNullOrBlank()) AsyncImage(thumbnail, "Sticker preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit, onError = { thumbnail = null })
                else Text(message.stickerEmoji?.takeIf { it.isNotBlank() } ?: "◇", fontSize = 40.sp)
                if (loading) CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.BottomEnd), strokeWidth = 2.dp)
            }
        }
        if (failed) TextButton(onClick = { attempt++ }) { Text("Retry sticker", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun AnimatedTgsSticker(json: String, active: Boolean, onError: () -> Unit) {
    val result = rememberLottieComposition(LottieCompositionSpec.JsonString(json))
    LaunchedEffect(result.error) { if (result.error != null) onError() }
    LottieAnimation(result.value, Modifier.fillMaxSize(), isPlaying = active,
        iterations = LottieConstants.IterateForever, enableMergePaths = true)
}

@Composable
private fun VideoSticker(path: String, active: Boolean, onError: () -> Unit) {
    val context = LocalContext.current
    val playing by rememberUpdatedState(active)
    val failed by rememberUpdatedState(onError)
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var surface by remember(path) { mutableStateOf<Surface?>(null) }
    var prepared by remember(path) { mutableStateOf(false) }
    fun release() {
        prepared = false
        player?.release(); player = null
        surface?.release(); surface = null
    }
    fun fit(view: TextureView, width: Int, height: Int) {
        if (view.width == 0 || view.height == 0 || width == 0 || height == 0) return
        val scale = minOf(view.width.toFloat() / width, view.height.toFloat() / height)
        view.setTransform(Matrix().apply { setScale(scale * width / view.width, scale * height / view.height, view.width / 2f, view.height / 2f) })
    }
    DisposableEffect(path) { onDispose { release() } }
    LaunchedEffect(active, prepared) { if (prepared) { if (active) player?.start() else player?.pause() } }
    AndroidView(factory = {
        TextureView(context).apply textureView@ {
            isOpaque = false
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    try {
                        surface = Surface(texture)
                        player = MediaPlayer().apply {
                            setSurface(surface)
                            setDataSource(path)
                            isLooping = true
                            setVolume(0f, 0f)
                            setOnPreparedListener { prepared = true; if (playing) it.start() }
                            setOnVideoSizeChangedListener { _, w, h -> fit(this@textureView, w, h) }
                            setOnErrorListener { _, _, _ -> failed(); true }
                            prepareAsync()
                        }
                    } catch (_: Exception) { failed() }
                }
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                    if (prepared) player?.let { fit(this@textureView, it.videoWidth, it.videoHeight) }
                }
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { release(); return true }
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            }
        }
    }, modifier = Modifier.fillMaxSize())
}
