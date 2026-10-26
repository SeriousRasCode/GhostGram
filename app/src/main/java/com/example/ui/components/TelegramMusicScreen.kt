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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.GhostMessage
import com.example.data.model.MusicLibraryUiState
import com.example.service.AudioPlaybackService
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage

@Composable
internal fun TelegramMusicScreen(
    state: MusicLibraryUiState,
    accountId: String,
    onRefresh: (Long) -> Unit,
    onLoadMedia: suspend (Int, (Float) -> Unit) -> String?,
    onLoadEmbeddedArtwork: suspend (String) -> String?,
    onCacheArtwork: suspend (String, String) -> String?,
    onSelectSource: (Long) -> Unit,
    onSetFavorite: (GhostMessage, Boolean) -> Unit,
    onImportChats: () -> Unit,
    profilePhotoPath: String?,
    onCurrentCoverChange: (Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playerRevision by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedSourceChatId by remember { mutableLongStateOf(state.selectedSourceChatId ?: 0L) }
    var libraryFilter by remember(selectedSourceChatId) { mutableStateOf(MusicLibraryFilter.ALL) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var loadingTrack by remember { mutableStateOf(false) }
    var loadingTrackKey by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(Float.NaN) }
    var embeddedArtwork by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(RepeatMode.OFF) }
    var queueOrder by remember(state.tracks) { mutableStateOf(state.tracks.indices.toList()) }
    var orderPosition by remember(state.tracks) { mutableIntStateOf(-1) }
    var position by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var lastEndedTrackKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.selectedSourceChatId) {
        selectedSourceChatId = state.selectedSourceChatId ?: 0L
    }

    DisposableEffect(context) {
        val future = MediaController.Builder(
            context.applicationContext,
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        ).buildAsync()
        var disposed = false
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { playerRevision++ }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Playback couldn't continue. Check the connection and try the track again."
                playerRevision++
            }
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
    LaunchedEffect(player, state.isLoadingSources, state.importedSources, state.tracks,
        state.tracksSourceChatId, state.selectedSourceChatId, state.hasSearched) {
        if (player == null || state.isLoadingSources || player.mediaItemCount == 0) return@LaunchedEffect
        val includedChatIds = state.importedSources.map { it.telegramChatId }.toSet()
        val scannedTrackKeys = if (state.hasSearched && state.tracksSourceChatId == state.selectedSourceChatId) {
            state.tracks.map { musicTrackKey(accountId, it) }.toSet()
        } else null
        val queueContainsExcludedTrack = (0 until player.mediaItemCount).any { index ->
            val item = player.getMediaItemAt(index)
            val chatId = item.mediaMetadata.extras?.getLong("chatId") ?: 0L
            chatId !in includedChatIds || (scannedTrackKeys != null && item.mediaId !in scannedTrackKeys)
        }
        if (queueContainsExcludedTrack) {
            player.stop()
            player.clearMediaItems()
            orderPosition = -1
        }
    }
    val currentItem = remember(player, playerRevision) { player?.currentMediaItem }
    val currentTrackKey = currentItem?.mediaId
    val activeTrackIndex = remember(state.tracks, currentTrackKey) {
        state.tracks.indexOfFirst { musicTrackKey(accountId, it) == currentTrackKey }
    }
    val currentTrack = state.tracks.getOrNull(activeTrackIndex)
    var nowPlayingCover by remember { mutableStateOf<Any?>(null) }
    val activeTrackKey = currentTrack?.let { musicTrackKey(accountId, it) } ?: currentItem?.mediaId
    LaunchedEffect(player, playerRevision, state.tracks, activeTrackIndex) {
        if (player == null) return@LaunchedEffect
        repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        if (player.mediaItemCount > 0) {
            val restoredOrder = (0 until player.mediaItemCount).mapNotNull { mediaIndex ->
                val id = player.getMediaItemAt(mediaIndex).mediaId
                state.tracks.indexOfFirst { musicTrackKey(accountId, it) == id }.takeIf { it >= 0 }
            }
            if (restoredOrder.isNotEmpty()) {
                queueOrder = restoredOrder
                orderPosition = restoredOrder.indexOf(activeTrackIndex).coerceAtLeast(0)
                val regularOrder = state.tracks.indices.filter { state.tracks[it].fileAttachment != null }
                shuffleEnabled = restoredOrder != regularOrder
            }
        }
    }
    LaunchedEffect(activeTrackKey, currentItem?.localConfiguration?.uri, currentTrack?.fileAttachment?.fileId, profilePhotoPath) {
        val fallback = profilePhotoPath.takeIf { currentTrack != null }
        nowPlayingCover = fallback
        onCurrentCoverChange(fallback)

        val item = currentItem?.takeIf { it.mediaId == activeTrackKey } ?: return@LaunchedEffect
        val trackKey = item.mediaId
        val track = currentTrack ?: return@LaunchedEffect
        var embedded = embeddedArtwork[trackKey] ?: onLoadEmbeddedArtwork(trackKey)

        if (embedded == null) {
            val itemPath = item.localConfiguration?.uri?.takeIf { it.scheme == "file" }?.path
                ?.takeIf { File(it).isFile }
            val attachmentPath = track.fileAttachment?.localPath?.takeIf { File(it).isFile }
            val audioPath = itemPath ?: attachmentPath ?: track.audioPath?.takeIf { File(it).isFile }
                ?: track.fileAttachment?.fileId?.takeIf { it > 0 }?.let { fileId -> onLoadMedia(fileId) { } }
            embedded = audioPath?.let { onCacheArtwork(trackKey, it) }
            embedded?.let { artwork -> embeddedArtwork = embeddedArtwork + (trackKey to artwork) }
        }

        if (activeTrackKey == currentItem?.mediaId) {
            nowPlayingCover = embedded ?: fallback
            onCurrentCoverChange(nowPlayingCover)
        }
    }
    LaunchedEffect(activeTrackIndex, queueOrder) {
        if (activeTrackIndex >= 0) orderPosition = queueOrder.indexOf(activeTrackIndex).coerceAtLeast(0)
    }
    val duration = (player?.duration ?: 0L).takeIf { it > 0L }?.toFloat() ?: 0f
    val isPlaying = player?.isPlaying == true
    val selectedTracks = state.tracks.takeIf { state.tracksSourceChatId == selectedSourceChatId }.orEmpty()
    val hasSearchedSelectedSource = state.hasSearched && state.tracksSourceChatId == selectedSourceChatId
    val searchedTracks = remember(selectedTracks, query) {
        val needle = query.trim()
        if (needle.isEmpty()) selectedTracks else selectedTracks.filter {
            it.audioTitle.orEmpty().contains(needle, true) ||
                it.audioPerformer.orEmpty().contains(needle, true) ||
                it.sourceTitle.contains(needle, true) ||
                it.fileAttachment?.fileName.orEmpty().contains(needle, true)
        }
    }
    val newTrackCutoff = remember(selectedSourceChatId) { System.currentTimeMillis() - MUSIC_NEW_TRACK_WINDOW_MS }
    val hotCount = selectedTracks.count { it.musicListenedMs >= MUSIC_HOT_MIN_LISTENED_MS }
    val visibleTracks = remember(searchedTracks, libraryFilter, newTrackCutoff) {
        when (libraryFilter) {
            MusicLibraryFilter.ALL -> searchedTracks
            MusicLibraryFilter.FAVORITE -> searchedTracks.filter { it.musicIsFavorite }
            MusicLibraryFilter.HOT -> searchedTracks.filter { it.musicListenedMs >= MUSIC_HOT_MIN_LISTENED_MS }
                .sortedWith(compareByDescending<GhostMessage> { it.musicListenedMs }.thenByDescending { it.musicLastPlayedAt })
            MusicLibraryFilter.NEW -> searchedTracks.filter { it.musicAddedAt >= newTrackCutoff }
                .sortedByDescending { it.musicAddedAt }
        }
    }

    LaunchedEffect(player, playerRevision, isSeeking) {
        if (!isSeeking) position = player?.currentPosition?.toFloat() ?: 0f
        while (player?.isPlaying == true && !isSeeking) {
            delay(350)
            position = player.currentPosition.toFloat()
        }
    }

    fun startTrack(index: Int) {
        if (index !in state.tracks.indices || player == null) {
            loadingTrackKey = null
            playbackError = "The music player is still connecting. Try again in a moment."
            return
        }
        val order = if (shuffleEnabled) {
            listOf(index) + state.tracks.indices.filter { it != index }.shuffled()
        } else {
            state.tracks.indices.toList()
        }
        val positionInOrder = order.indexOf(index)
        scope.launch {
            startTrackAt(
                positionInOrder, order, state.tracks, accountId, player, onLoadMedia,
                { loadingTrack = it; if (!it) loadingTrackKey = null }, { playbackError = it },
                { downloadProgress = it },
                { queueOrder = it }, { orderPosition = it },
                { track, path ->
                    scope.launch {
                        val key = musicTrackKey(accountId, track)
                        onCacheArtwork(key, path)?.let { artworkPath ->
                            embeddedArtwork = (embeddedArtwork + (key to artworkPath)).entries.toList().takeLast(12).associate { it.toPair() }
                        }
                    }
                }
            )
        }
    }

    fun moveTrack(delta: Int) {
        if (player == null || state.tracks.isEmpty()) return
        if (orderPosition < 0) {
            startTrack(if (delta > 0) 0 else state.tracks.lastIndex)
            return
        }
        if (delta > 0 && player.hasNextMediaItem()) player.seekToNextMediaItem()
        else if (delta < 0 && player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Music sources") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose an imported group or channel to scan.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.importedSources.isEmpty()) {
                        Text("No imported groups or channels yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.importedSources, key = { it.telegramChatId }) { source ->
                                val selected = source.telegramChatId == selectedSourceChatId
                                Surface(shape = RoundedCornerShape(14.dp),
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedSourceChatId = source.telegramChatId
                                        onSelectSource(source.telegramChatId)
                                    }) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                            Text(if (source.type.name == "CHANNEL") "Channel" else "Group",
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (selected) Icon(Icons.Default.CheckCircle, "Selected source", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = selectedSourceChatId != 0L && !state.isLoading,
                    onClick = { onRefresh(selectedSourceChatId); showSourceDialog = false }) { Text("Scan selected") }
            },
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false; onImportChats() }) { Text("Add chat") }
            },
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
    VerticalPager(state = pagerState, modifier = modifier.fillMaxSize()) { page ->
        if (page == 0) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GHOSTGRAM · AUDIO", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text("Swipe up for your library", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            NowPlayingCard(
                track = currentTrack,
                coverPath = nowPlayingCover,
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
                    if (currentItem != null && (currentTrack == null || currentTrackKey == musicTrackKey(accountId, currentTrack))) {
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
                    if (current >= 0 && player != null) {
                        val wasPlaying = player.isPlaying
                        val currentPosition = player.currentPosition.coerceAtLeast(0L)
                        val queuedTracks = queueOrder.mapNotNull { state.tracks.getOrNull(it) }
                            .filter { it.fileAttachment != null }
                        val queuedPosition = queueOrder.take(orderPosition)
                            .count { state.tracks.getOrNull(it)?.fileAttachment != null }
                        scope.launch {
                            player.setMediaItems(queuedTracks.map { musicMediaItem(accountId, it) },
                                queuedPosition.coerceIn(0, (queuedTracks.size - 1).coerceAtLeast(0)), currentPosition)
                            player.prepare()
                            if (wasPlaying) player.play() else player.pause()
                        }
                    }
                },
                onToggleRepeat = {
                    repeatMode = (repeatMode + 1) % 3
                    player?.repeatMode = when (repeatMode) {
                        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                },
                error = playbackError
            )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search tracks, artists, chats") },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onImportChats, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, "Add imported chat") }
                        IconButton(onClick = { showSourceDialog = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Music source options") }
                    }
                }
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MusicLibraryFilter.entries.forEach { filter ->
                        val selected = libraryFilter == filter
                        Surface(
                            onClick = { libraryFilter = filter },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
                                else Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        filter.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (filter == MusicLibraryFilter.HOT) {
                                        Spacer(Modifier.width(4.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                                        ) {
                                            Text(
                                                hotCount.toString(),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Text(
                                    filter.title,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (state.isLoadingSources) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.isLoading) {
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = if (state.totalChats == 0) 0f else state.searchedChats.toFloat() / state.totalChats,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Scanning ${state.importedSources.firstOrNull { it.telegramChatId == selectedSourceChatId }?.title ?: "selected chat"} for audio…",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error?.let { error -> InlineMessageState(message = error) }
            when {
                !state.isLoading && state.importedSources.isEmpty() -> InlineMessageState("Import a group or channel to browse its music.", onRetry = onImportChats)
                !state.isLoading && !hasSearchedSelectedSource -> InlineMessageState("Choose a group or channel, then scan it to load its tracks.")
                !state.isLoading && selectedTracks.isEmpty() -> InlineMessageState("No audio tracks were found in this chat.")
                !state.isLoading && visibleTracks.isEmpty() -> InlineMessageState(
                    when {
                        searchedTracks.isEmpty() -> "No tracks match that search."
                        libraryFilter == MusicLibraryFilter.FAVORITE -> "No favorites here yet. Tap the heart on a track to save it."
                        libraryFilter == MusicLibraryFilter.HOT -> "Listen to a track for at least a minute to add it to Hot. Longer listening time ranks it higher."
                        libraryFilter == MusicLibraryFilter.NEW -> "New tracks added in the last 30 days will appear here."
                        else -> "No tracks match that search."
                    }
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleTracks, key = { musicTrackKey(accountId, it) }) { track ->
                        MusicTrackRow(track, musicTrackKey(accountId, track), musicTrackKey(accountId, track) == currentTrackKey,
                            isPlaying && musicTrackKey(accountId, track) == currentTrackKey,
                            if (musicTrackKey(accountId, track) == currentTrackKey) position else null,
                            loadingTrack && loadingTrackKey == musicTrackKey(accountId, track),
                            downloadProgress,
                            if (musicTrackKey(accountId, track) == currentTrackKey) nowPlayingCover
                            else embeddedArtwork[musicTrackKey(accountId, track)],
                            profilePhotoPath,
                            onLoadEmbeddedArtwork,
                            track.musicIsFavorite,
                            track.musicListenedMs.takeIf { libraryFilter == MusicLibraryFilter.HOT },
                            onToggleFavorite = { onSetFavorite(track, !track.musicIsFavorite) },
                            onClick = {
                                loadingTrackKey = musicTrackKey(accountId, track)
                                downloadProgress = Float.NaN
                                playbackError = null
                                startTrack(selectedTracks.indexOf(track))
                            })
                    }
                }
            }
        }
    }
}

}

private object RepeatMode {
    const val OFF = 0
    const val ALL = 1
    const val ONE = 2
}

private enum class MusicLibraryFilter(val title: String, val icon: ImageVector) {
    ALL("All", Icons.Default.LibraryMusic),
    FAVORITE("Favorites", Icons.Default.FavoriteBorder),
    HOT("Hot", Icons.Default.Whatshot),
    NEW("New", Icons.Default.FiberNew)
}

private const val MUSIC_NEW_TRACK_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
private const val MUSIC_HOT_MIN_LISTENED_MS = 60_000L

private suspend fun startTrackAt(
    position: Int,
    order: List<Int>,
    tracks: List<GhostMessage>,
    accountId: String,
    player: MediaController,
    onLoadMedia: suspend (Int, (Float) -> Unit) -> String?,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onDownloadProgress: (Float) -> Unit,
    onOrderChanged: (List<Int>) -> Unit,
    onPositionChanged: (Int) -> Unit,
    onTrackReady: (GhostMessage, String) -> Unit
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
            ?: onLoadMedia(attachment.fileId, onDownloadProgress)
        if (path == null || !File(path).exists()) {
            onError("Couldn't download this track from Telegram. Tap it to retry.")
            return
        }
        onTrackReady(track, path)
        val items = order.mapIndexedNotNull { index, itemTrackIndex ->
            tracks.getOrNull(itemTrackIndex)?.takeIf { it.fileAttachment != null }?.let { queuedTrack ->
                musicMediaItem(accountId, queuedTrack, if (index == position) path else null)
            }
        }
        val queuePosition = order.take(position).count { tracks.getOrNull(it)?.fileAttachment != null }
        if (items.isEmpty()) return
        player.setMediaItems(items, queuePosition.coerceIn(items.indices), 0L)
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
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

private fun musicMediaItem(accountId: String, track: GhostMessage, localPath: String? = null): MediaItem {
    val attachment = requireNotNull(track.fileAttachment)
    val extras = Bundle().apply {
        putString("accountId", accountId)
        putLong("chatId", track.telegramChatId)
        putLong("messageId", track.telegramMsgId)
        putInt("coverFileId", track.audioCoverFileId)
    }
    val cachedPath = (localPath ?: attachment.localPath ?: track.audioPath)?.takeIf { File(it).exists() }
    val uri = cachedPath?.let { Uri.fromFile(File(it)) }
        ?: Uri.Builder().scheme("ghostgram").authority(accountId).appendPath(attachment.fileId.toString()).build()
    return MediaItem.Builder()
        .setMediaId(musicTrackKey(accountId, track))
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(musicTrackTitle(track))
            .setArtist(musicTrackArtist(track))
            .setAlbumTitle(track.sourceTitle)
            .setExtras(extras)
            .build())
        .build()
}

@Composable
private fun NowPlayingCard(
    track: GhostMessage?,
    coverPath: Any?,
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
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF211C45), Color(0xFF6842A5), Color(0xFFCB6189)))),
                contentAlignment = Alignment.Center
            ) {
                if (coverPath != null) {
                    AsyncImage(model = coverPath, contentDescription = "Artwork for $title",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),
                        contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
                }
                if (coverPath == null) {
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
private fun MusicTrackRow(
    track: GhostMessage,
    trackKey: String,
    active: Boolean,
    playing: Boolean,
    playbackPosition: Float?,
    isDownloading: Boolean,
    downloadProgress: Float,
    embeddedArtwork: Any?,
    profilePhotoPath: String?,
    onLoadEmbeddedArtwork: suspend (String) -> String?,
    isFavorite: Boolean,
    listenedTimeMs: Long?,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    var coverPath by remember(trackKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(trackKey) {
        coverPath = onLoadEmbeddedArtwork(trackKey)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Play ${musicTrackTitle(track)}", onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF5949AC), Color(0xFFD05D8B)))),
                contentAlignment = Alignment.Center) {
                val art = embeddedArtwork ?: coverPath ?: profilePhotoPath
                if (art != null) AsyncImage(art, "Artwork for ${musicTrackTitle(track)}",
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
                else Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
                if (playing) Surface(Modifier.align(Alignment.BottomEnd).padding(2.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "Now playing", tint = Color.White,
                        modifier = Modifier.padding(3.dp).size(13.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(musicTrackTitle(track), style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${musicTrackArtist(track)}  ·  ${track.sourceTitle}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                listenedTimeMs?.let {
                    Text(musicListeningLabel(it), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
            if (isDownloading) {
                Column(Modifier.width(70.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (downloadProgress.isFinite()) "${(downloadProgress * 100).toInt().coerceIn(0, 100)}%" else "Downloading",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    if (downloadProgress.isFinite()) {
                        LinearProgressIndicator(progress = { downloadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
                    }
                }
            }
            Text(if (playbackPosition != null) "${musicTime(playbackPosition)} / ${musicTime(track.audioDurationSeconds * 1000f)}"
                else musicTime(track.audioDurationSeconds * 1000f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove ${musicTrackTitle(track)} from favorites" else "Add ${musicTrackTitle(track)} to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
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

private fun musicListeningLabel(listenedMs: Long): String {
    val totalMinutes = (listenedMs.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours hr ${minutes} min listened" else "$totalMinutes min listened"
}
