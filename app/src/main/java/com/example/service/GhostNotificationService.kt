package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.GhostDatabase
import com.example.data.local.GhostPreferences
import com.example.data.model.ChatSourceType
import com.example.data.remote.TelegramGhostClient
import com.example.data.remote.TelegramUserClient
import com.example.data.repository.GhostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class GhostNotificationService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private lateinit var repository: GhostRepository
    private lateinit var userClient: TelegramUserClient

    companion object {
        private const val CHANNEL_ID = "ghost_feed_notifications"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        try {
            Log.d("GhostNotification", "Service Creating...")
            createNotificationChannel()
            
            // Start as foreground service IMMEDIATELY to satisfy system requirements
            val notification = createForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val db = GhostDatabase.getDatabase(this)
            val prefs = GhostPreferences(this)
            val remoteClient = TelegramGhostClient()
            userClient = TelegramUserClient.getInstance(this)
            repository = GhostRepository(db, prefs, remoteClient, userClient)

            startListeningForUpdates()
        } catch (e: Exception) {
            Log.e("GhostNotification", "Critical service failure: ${e.message}", e)
            // If foreground start fails, we must stop or we'll get an ANR/Crash
            stopSelf()
        }
    }

    private fun createForegroundNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("GhostGram Focus Mode")
            .setContentText("Monitoring your focus feeds for updates...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startListeningForUpdates() {
        serviceScope.launch {
            userClient.updates.collect { update ->
                if (update is TdApi.UpdateNewMessage) {
                    handleNewMessage(update.message)
                }
            }
        }
    }

    private suspend fun handleNewMessage(message: TdApi.Message) {
        // Check if this message belongs to a Focus Source
        val sources = repository.getAllSources().first()
        val source = sources.find { it.telegramChatId == message.chatId }
            ?: if (message.chatId == userClient.fetchMe().getOrNull()?.userId) {
                // Handle Saved Messages
                sources.find { it.id == -1L || it.type == ChatSourceType.SAVED_MESSAGES }
            } else null

        if (source != null) {
            val config = repository.getConfig()
            if (!config.notificationsEnabled) return

            // Skip notifications for messages sent by 'Me' from other devices
            val meId = repository.getUserSession().userId
            val isFromMe = (message.senderId as? TdApi.MessageSenderUser)?.userId == meId
            if (isFromMe) return
            val senderName = getSenderName(message)
            val text = getMessageText(message.content)
            
            val displayTitle = if (config.showNotificationPreview) source.title else "New Focus Update"
            val displayText = if (config.showNotificationPreview) {
                "$senderName: $text"
            } else {
                "Open GhostGram to view new messages in ${source.title}"
            }

            showNotification(
                title = displayTitle,
                text = displayText,
                chatId = message.chatId
            )
            
            // Also refresh the source in the background database
            repository.refreshSource(source, 0)
        }
    }

    private suspend fun getSenderName(message: TdApi.Message): String {
        return (message.senderId as? TdApi.MessageSenderUser)?.let { senderUser ->
            try {
                val user = userClient.execute(TdApi.GetUser(senderUser.userId))
                "${user.firstName} ${user.lastName}".trim().ifBlank { "Focus Contact" }
            } catch (e: Exception) { "Focus Contact" }
        } ?: (message.senderId as? TdApi.MessageSenderChat)?.let { senderChat ->
            try {
                val chat = userClient.execute(TdApi.GetChat(senderChat.chatId))
                chat.title
            } catch (e: Exception) { "Focus Source" }
        } ?: "Focus Source"
    }

    private fun getMessageText(content: TdApi.MessageContent): String {
        return when (content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessagePhoto -> {
                val cap = content.caption.text
                if (cap.isNotBlank()) cap else "[Photo]"
            }
            is TdApi.MessageVideo -> {
                val cap = content.caption.text
                if (cap.isNotBlank()) cap else "[Video]"
            }
            is TdApi.MessageDocument -> {
                val cap = content.caption.text
                val fn = content.document.fileName
                if (cap.isNotBlank()) "$fn: $cap" else "[File: $fn]"
            }
            is TdApi.MessageSticker -> "Sticker ${content.sticker.emoji}"
            is TdApi.MessageVoiceNote -> "[Voice Message]"
            is TdApi.MessageAudio -> "[Audio Message]"
            else -> "[New Update]"
        }
    }

    private fun showNotification(title: String, text: String, chatId: Long) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("chatId", chatId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for actual message alerts
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use a unique ID for each chat notification to avoid overwriting
        notificationManager.notify((chatId % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Focus Feed Updates"
            val descriptionText = "Notifications for your added GhostGram groups and channels"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
