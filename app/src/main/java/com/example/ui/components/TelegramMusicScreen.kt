package com.example.ui.components

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.GhostMessage
import com.example.data.model.MusicLibraryUiState
import com.example.service.AudioPlaybackService
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun TelegramMusicScreen(
    state: MusicLibraryUiState,
    accountId: String,
    onRefresh: () -> Unit,
    onLoadMedia: suspend (Int) -> String?,
    onImportChats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playerRevision by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var loadingTrack by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(RepeatMode.OFF) }
    var queueOrder by remember(state.tracks) { mutableStateOf(state.tracks.indices.toList()) }
    var orderPosition by remember(state.tracks) { mutableIntStateOf(-1) }
    var position by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var lastEndedTrackKey by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        val future = MediaController.Builder(
            context.applicationContext,
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        ).buildAsync()
        var disposed = false
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { playerRevision++ }
        }
        future.addListener({
            if (!disposed) runCatching { future.get() }.onSuccess {
                controller = it
                it.addListener(listener)
                playerRevision++
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            controller?.removeListener(listener)
            controller = null
            MediaController.releaseFuture(future)
        }
    }

    val player = controller
    val currentItem = remember(player, playerRevision) { player?.currentMediaItem }
    val currentTrackKey = currentItem?.mediaId
    val activeTrackIndex = remember(state.tracks, currentTrackKey) {
        state.tracks.indexOfFirst { musicTrackKey(accountId, it) == currentTrackKey }
    }
    val currentTrack = state.tracks.getOrNull(activeTrackIndex)
    val duration = (player?.duration ?: 0L).takeIf { it > 0L }?.toFloat() ?: 0f
    val isPlaying = player?.isPlaying == true
    val visibleTracks = remember(state.tracks, query) {
        val needle = query.trim()
        if (needle.isEmpty()) state.tracks else state.tracks.filter {
            it.audioTitle.orEmpty().contains(needle, true) ||
                it.audioPerformer.orEmpty().contains(needle, true) ||
                it.sourceTitle.contains(needle, true) ||
                it.fileAttachment?.fileName.orEmpty().contains(needle, true)
        }
    }

    LaunchedEffect(player, playerRevision, isSeeking) {
        if (!isSeeking) position = player?.currentPosition?.toFloat() ?: 0f
        while (player?.isPlaying == true && !isSeeking) {
            delay(350)
            position = player.currentPosition.toFloat()
        }
    }

    LaunchedEffect(player, playerRevision, activeTrackIndex, orderPosition, repeatMode) {
        val activePlayer = player ?: return@LaunchedEffect
        val endedKey = activePlayer.currentMediaItem?.mediaId
        if (activePlayer.playbackState == Player.STATE_ENDED && repeatMode != RepeatMode.ONE && endedKey != lastEndedTrackKey) {
            lastEndedTrackKey = endedKey
            val nextPosition = orderPosition + 1
            when {
                nextPosition < queueOrder.size -> startTrackAt(
                    nextPosition, queueOrder, state.tracks, accountId, player, onLoadMedia,
                    { loadingTrack = it }, { playbackError = it },
                    { queueOrder = it }, { orderPosition = it }
                )
                repeatMode == RepeatMode.ALL && queueOrder.isNotEmpty() -> startTrackAt(
                    0, queueOrder, state.tracks, accountId, player, onLoadMedia,
                    { loadingTrack = it }, { playbackError = it },
                    { queueOrder = it }, { orderPosition = it }
                )
            }
        }
    }

    fun startTrack(index: Int) {
        if (index !in state.tracks.indices || player == null) return
        val order = if (shuffleEnabled) {
            listOf(index) + state.tracks.indices.filter { it != index }.shuffled()
        } else {
            state.tracks.indices.toList()
        }
        val positionInOrder = order.indexOf(index)
        scope.launch {
            startTrackAt(
                positionInOrder, order, state.tracks, accountId, player, onLoadMedia,
                { loadingTrack = it }, { playbackError = it },
                { queueOrder = it }, { orderPosition = it }
            )
        }
    }

    fun moveTrack(delta: Int) {
        if (player == null || state.tracks.isEmpty()) return
        if (orderPosition < 0) {
            startTrack(if (delta > 0) 0 else state.tracks.lastIndex)
            return
        }
        var nextPosition = orderPosition + delta
        if (nextPosition !in queueOrder.indices) {
            if (repeatMode != RepeatMode.ALL) return
            nextPosition = if (delta > 0) 0 else queueOrder.lastIndex
        }
        scope.launch {
            startTrackAt(
                nextPosition, queueOrder, state.tracks, accountId, player, onLoadMedia,
                { loadingTrack = it }, { playbackError = it },
                { queueOrder = it }, { orderPosition = it }
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR LIBRARY", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text("Music", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Audio from your imported Telegram chats", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh music library")
                }
            }
        }

        item {
            NowPlayingCard(
                track = currentTrack,
                fallbackTitle = currentItem?.mediaMetadata?.title?.toString(),
                fallbackArtist = currentItem?.mediaMetadata?.artist?.toString(),
                isPlaying = isPlaying,
                isLoading = loadingTrack || player?.playbackState == Player.STATE_BUFFERING,
                position = position,
                duration = duration,
                onSeekStart = { isSeeking = true },
                onSeek = { position = it },
                onSeekEnd = {
                    if (duration > 0f) player?.seekTo(position.coerceIn(0f, duration).toLong())
                    isSeeking = false
                },
                onPlayPause = {
                    if (currentTrack != null && currentTrackKey == musicTrackKey(accountId, currentTrack)) {
                        if (isPlaying) player?.pause() else player?.play()
                    } else if (state.tracks.isNotEmpty()) startTrack(0)
                },
                onPrevious = { moveTrack(-1) },
                onNext = { moveTrack(1) },
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                onToggleShuffle = {
                    val enableShuffle = !shuffleEnabled
                    shuffleEnabled = enableShuffle
                    val current = activeTrackIndex
                    queueOrder = when {
                        !enableShuffle -> state.tracks.indices.toList()
                        current >= 0 -> listOf(current) + state.tracks.indices.filter { it != current }.shuffled()
                        else -> state.tracks.indices.shuffled()
                    }
                    orderPosition = if (current >= 0) queueOrder.indexOf(current) else -1
                },
                onToggleRepeat = {
                    repeatMode = (repeatMode + 1) % 3
                    player?.repeatMode = if (repeatMode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                },
                error = playbackError
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search tracks, artists, chats") },
                label = { Text("Search your library") }
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Imported chat music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${state.tracks.size} ${if (state.tracks.size == 1) "track" else "tracks"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.isLoading && state.totalChats > 0) {
                    Text("${state.searchedChats}/${state.totalChats} chats", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (state.isLoading) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = if (state.totalChats == 0) 0f else state.searchedChats.toFloat() / state.totalChats,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(if (state.totalChats == 0) "Finding imported chats…" else "Scanning imported chats for music…",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        state.error?.let { error ->
            item { InlineMessageState(message = error, onRetry = onRefresh) }
        }

        if (!state.isLoading && state.tracks.isEmpty()) {
            item {
                EmptyMusicLibrary(onImportChats = onImportChats)
            }
        } else if (!state.isLoading && visibleTracks.isEmpty()) {
            item { InlineMessageState(message = "No tracks match that search.") }
        } else {
            items(visibleTracks, key = { musicTrackKey(accountId, it) }) { track ->
                MusicTrackRow(
                    track = track,
                    active = musicTrackKey(accountId, track) == currentTrackKey,
                    playing = isPlaying && musicTrackKey(accountId, track) == currentTrackKey,
                    onClick = { startTrack(state.tracks.indexOf(track)) }
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private object RepeatMode {
    const val OFF = 0
    const val ALL = 1
    const val ONE = 2
}

private suspend fun startTrackAt(
    position: Int,
    order: List<Int>,
    tracks: List<GhostMessage>,
    accountId: String,
    player: MediaController,
    onLoadMedia: suspend (Int) -> String?,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onOrderChanged: (List<Int>) -> Unit,
    onPositionChanged: (Int) -> Unit
) {
    val trackIndex = order.getOrNull(position) ?: return
    val track = tracks.getOrNull(trackIndex) ?: return
    val attachment = track.fileAttachment ?: run {
        onError("This Telegram audio has no downloadable file.")
        return
    }
    onLoadingChanged(true)
    onError(null)
    try {
        val path = attachment.localPath?.takeIf { File(it).exists() }
            ?: onLoadMedia(attachment.fileId)
        if (path == null || !File(path).exists()) {
            onError("Couldn't download this track from Telegram. Tap it to retry.")
            return
        }
        val extras = Bundle().apply {
            putString("accountId", accountId)
            putLong("chatId", track.telegramChatId)
            putLong("messageId", track.telegramMsgId)
        }
        val title = track.audioTitle?.takeIf { it.isNotBlank() }
            ?: attachment.fileName.substringBeforeLast('.').ifBlank { "Unknown track" }
        val artist = track.audioPerformer?.takeIf { it.isNotBlank() } ?: track.sourceTitle
        val item = MediaItem.Builder()
            .setMediaId(musicTrackKey(accountId, track))
            .setUri(Uri.fromFile(File(path)))
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(track.sourceTitle)
                .setExtras(extras)
                .build())
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
        onOrderChanged(order)
        onPositionChanged(position)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onError(error.message ?: "Couldn't play this Telegram track.")
    } finally {
        onLoadingChanged(false)
    }
}

@Composable
private fun NowPlayingCard(
    track: GhostMessage?,
    fallbackTitle: String?,
    fallbackArtist: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: Float,
    duration: Float,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    error: String?
) {
    val title = track?.let(::musicTrackTitle) ?: fallbackTitle ?: "Choose a track"
    val artist = track?.let(::musicTrackArtist) ?: fallbackArtist ?: "Your Telegram library is ready"
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.24f).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF211C45), Color(0xFF6842A5), Color(0xFFCB6189)))),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.align(Alignment.TopEnd).padding(18.dp).size(112.dp).background(Color.White.copy(alpha = .08f), CircleShape))
                Box(Modifier.align(Alignment.BottomStart).padding(18.dp).size(82.dp).background(Color(0xFFFFB86C).copy(alpha = .18f), CircleShape))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (isPlaying) "NOW PLAYING" else "GHOSTGRAM MUSIC", color = Color.White.copy(alpha = .8f),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                }
                Surface(shape = CircleShape, color = Color.White.copy(alpha = .16f), modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "Music visualizer", tint = Color.White,
                        modifier = Modifier.padding(10.dp).size(20.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            Slider(
                value = position.coerceIn(0f, duration.coerceAtLeast(1f)),
                onValueChange = { onSeekStart(); onSeek(it) },
                onValueChangeFinished = onSeekEnd,
                enabled = track != null && duration > 0f,
                valueRange = 0f..duration.coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth().height(32.dp).semantics {
                    contentDescription = "Track position"
                    stateDescription = "${musicTime(position)} of ${musicTime(duration)}"
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(musicTime(position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(musicTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(Icons.Default.Shuffle, contentDescription = if (shuffleEnabled) "Turn shuffle off" else "Turn shuffle on",
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(30.dp))
                }
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(62.dp)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause music" else "Play music", modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next track", modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = onToggleRepeat) {
                    Icon(if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = when (repeatMode) {
                            RepeatMode.ONE -> "Repeat one track"
                            RepeatMode.ALL -> "Repeat all tracks"
                            else -> "Repeat off"
                        },
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(when (repeatMode) {
                RepeatMode.ONE -> "Repeating this track"
                RepeatMode.ALL -> "Repeating the queue"
                else -> if (shuffleEnabled) "Shuffle is on" else "Playing in order"
            }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun MusicTrackRow(track: GhostMessage, active: Boolean, playing: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Play ${musicTrackTitle(track)}", onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF5949AC), Color(0xFFD05D8B)))),
                contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(musicTrackTitle(track), style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${musicTrackArtist(track)}  ·  ${track.sourceTitle}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(musicTime(track.audioDurationSeconds * 1000f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun EmptyMusicLibrary(onImportChats: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp))
            Text("No music found yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Import a Telegram chat with audio files, then refresh your library.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Button(onClick = onImportChats) { Text("Import a chat") }
        }
    }
}

@Composable
private fun InlineMessageState(message: String, onRetry: (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            if (onRetry != null) androidx.compose.material3.TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

private fun musicTrackKey(accountId: String, track: GhostMessage): String =
    "$accountId:${track.telegramChatId}:${track.telegramMsgId}"

private fun musicTrackTitle(track: GhostMessage): String = track.audioTitle?.takeIf { it.isNotBlank() }
    ?: track.fileAttachment?.fileName?.substringBeforeLast('.')?.ifBlank { null }
    ?: "Unknown track"

private fun musicTrackArtist(track: GhostMessage): String = track.audioPerformer?.takeIf { it.isNotBlank() }
    ?: track.sourceTitle.ifBlank { "Unknown artist" }

private fun musicTime(milliseconds: Float): String {
    val seconds = (milliseconds.coerceAtLeast(0f) / 1000f).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
