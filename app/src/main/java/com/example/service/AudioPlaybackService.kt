package com.example.service

import android.app.PendingIntent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.common.Player
import android.os.SystemClock
import com.example.R
import com.example.data.local.GhostAccounts
import com.example.data.repository.GhostRepository
import android.net.Uri
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import com.example.util.MessageNotificationTarget

/** The service owns audio; recycled message rows only attach controllers. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("ghost_audio_playback")
            .setChannelName(R.string.audio_playback_channel)
            .build().apply { setSmallIcon(R.drawable.ic_stat_audio) })
        val resolvingDataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(this),
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    if (uri.scheme != "ghostgram") return dataSpec
                    val accountId = uri.authority ?: throw java.io.IOException("Missing Telegram account")
                    val fileId = uri.lastPathSegment?.toIntOrNull() ?: throw java.io.IOException("Invalid Telegram audio")
                    val path = runBlocking(Dispatchers.IO) {
                        GhostRepository.getInstance(this@AudioPlaybackService, accountId).downloadFile(fileId).getOrThrow()
                    }
                    return DataSpec.Builder().setUri(Uri.fromFile(File(path)))
                        .setPosition(dataSpec.position)
                        .setLength(dataSpec.length)
                        .setKey(dataSpec.key)
                        .build()
                }
            })
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingDataSource))
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    return if (controller.packageName == packageName || controller.isTrusted) {
                        super.onConnect(session, controller)
                    } else MediaSession.ConnectionResult.reject()
                }
            })
            .build()
        data class ListeningTrack(val accountId: String, val chatId: Long, val messageId: Long)
        var listeningTrack: ListeningTrack? = null
        var lastPositionMs = 0L
        var lastSampleRealtimeMs = SystemClock.elapsedRealtime()
        var pendingListeningMs = 0L

        fun persistListeningTime() {
            val track = listeningTrack ?: return
            val listenedMs = pendingListeningMs
            if (listenedMs <= 0L) return
            pendingListeningMs = 0L
            serviceScope.launch {
                GhostRepository.getInstance(this@AudioPlaybackService, track.accountId)
                    .recordMusicListeningTime(track.chatId, track.messageId, listenedMs)
            }
        }

        fun sampleListeningTime(allowFinalActiveSample: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            val position = player.currentPosition.coerceAtLeast(0L)
            val isPlaying = player.isPlaying
            val extras = player.currentMediaItem?.mediaMetadata?.extras
            val nextTrack = extras?.getString("accountId")?.let { accountId ->
                val chatId = extras.getLong("chatId")
                val messageId = extras.getLong("messageId")
                if (chatId != 0L && messageId != 0L) ListeningTrack(accountId, chatId, messageId) else null
            }
            val elapsed = (now - lastSampleRealtimeMs).coerceAtLeast(0L)

            if ((!isPlaying && !allowFinalActiveSample) || nextTrack == null) {
                persistListeningTime()
                listeningTrack = null
                lastPositionMs = position
                lastSampleRealtimeMs = now
                return
            }

            if (nextTrack != listeningTrack) {
                persistListeningTime()
                if (!isPlaying) {
                    listeningTrack = null
                    lastPositionMs = position
                    lastSampleRealtimeMs = now
                    return
                }
                listeningTrack = nextTrack
                lastPositionMs = position
                lastSampleRealtimeMs = now
                return
            }

            val mediaAdvance = position - lastPositionMs
            val expectedAdvance = (elapsed * player.playbackParameters.speed).toLong()
            // Count only normal forward playback. Large jumps are seeks; pauses and buffering
            // are excluded because Media3 reports isPlaying=false for those states.
            if (elapsed in 1L..5_000L && mediaAdvance in 1L..(expectedAdvance + 1_500L)) {
                pendingListeningMs += elapsed.coerceAtMost(2_000L)
            }
            lastPositionMs = position
            lastSampleRealtimeMs = now
            if (!isPlaying) {
                persistListeningTime()
                listeningTrack = null
                return
            }
            if (pendingListeningMs >= 10_000L) persistListeningTime()
        }

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                sampleListeningTime()
                val extras = item?.mediaMetadata?.extras ?: return
                val target = MessageNotificationTarget(
                    extras.getString("accountId") ?: return,
                    extras.getLong("chatId"), extras.getLong("messageId"))
                session?.setSessionActivity(PendingIntent.getActivity(this@AudioPlaybackService, 0,
                    target.toIntent(this@AudioPlaybackService),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                sampleListeningTime(allowFinalActiveSample = !isPlaying)
            }
        })
        serviceScope.launch {
            while (true) {
                delay(1_000)
                sampleListeningTime()
            }
        }
        serviceScope.launch {
            GhostAccounts.get(this@AudioPlaybackService).accounts.collect { accounts ->
                val accountId = player.currentMediaItem?.mediaMetadata?.extras?.getString("accountId")
                if (accountId != null && accounts.none { it.id == accountId && it.session.isLoggedIn }) {
                    player.stop()
                    player.clearMediaItems()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // MediaSessionService keeps ongoing playback alive when the task is swiped away.
    override fun onDestroy() {
        serviceScope.cancel()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
