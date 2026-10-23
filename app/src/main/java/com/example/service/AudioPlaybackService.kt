package com.example.service

import android.app.PendingIntent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.common.Player
import com.example.R
import com.example.data.local.GhostAccounts
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
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        session = MediaSession.Builder(this, player)
            .setMediaButtonPreferences(listOf(
                CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                    .setDisplayName("Back 10 seconds").setPlayerCommand(Player.COMMAND_SEEK_BACK)
                    .setSlots(CommandButton.SLOT_BACK).build(),
                CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                    .setDisplayName("Forward 10 seconds").setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                    .setSlots(CommandButton.SLOT_FORWARD).build()))
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
