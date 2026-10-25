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
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                val extras = item?.mediaMetadata?.extras ?: return
                val target = MessageNotificationTarget(
                    extras.getString("accountId") ?: return,
                    extras.getLong("chatId"), extras.getLong("messageId"))
                session?.setSessionActivity(PendingIntent.getActivity(this@AudioPlaybackService, 0,
                    target.toIntent(this@AudioPlaybackService),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
        })
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
