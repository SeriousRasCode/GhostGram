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
import com.example.data.local.GhostAccounts
import com.example.data.model.ChatSourceType
import com.example.data.remote.TelegramGhostClient
import com.example.data.remote.TelegramUserClient
import com.example.data.repository.GhostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.awaitCancellation
import org.drinkless.tdlib.TdApi

class GhostNotificationService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    

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
            GhostAccounts.get(this@GhostNotificationService).accounts.collectLatest { accounts ->
                coroutineScope {
                    accounts.filter { it.session.isLoggedIn }.forEach { account -> launch {
                        val client = TelegramUserClient.getInstance(this@GhostNotificationService, account.id)
                        val repository = GhostRepository(
                            GhostDatabase.getDatabase(this@GhostNotificationService, account.id),
                            GhostPreferences(this@GhostNotificationService, account.id), TelegramGhostClient(), client)
                        try {
                            coroutineScope {
                                launch {
                                    client.authStateFlow.collectLatest { state ->
                                        if (state is TdApi.AuthorizationStateReady) {
                                            try { repository.refreshAllSources() }
                                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                            catch (e: Exception) { Log.w("GhostNotification", "Account refresh failed", e) }
                                        }
                                    }
                                }
                                client.updates.collect { update ->
                                    if (update is TdApi.UpdateNewMessage) {
                                        try { handleNewMessage(account.id, repository, client, update.message) }
                                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                        catch (e: Exception) { Log.w("GhostNotification", "Could not notify account update", e) }
                                    }
                                }
                            }
                        } finally { repository.close() }
                    } }
                    awaitCancellation()
                }
            }
        }
    }

    private suspend fun handleNewMessage(accountId: String, repository: GhostRepository, userClient: TelegramUserClient, message: TdApi.Message) {
        // Check if this message belongs to a Focus Source
        val sources = repository.getAllSources().first()
        val source = sources.find { it.telegramChatId == message.chatId }
            ?: if (message.chatId == repository.getUserSession().userId) {
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
            val senderName = getSenderName(userClient, message)
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
                chatId = message.chatId,
                accountId = accountId
            )
            
            // The repository applies the live update independently of notification preferences.
        }
    }

    private suspend fun getSenderName(userClient: TelegramUserClient, message: TdApi.Message): String {
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
            is TdApi.MessageVoiceNote -> content.caption.text.ifBlank { "Voice message" }
            is TdApi.MessageAudio -> content.caption.text.ifBlank { "Audio message" }
            else -> "[New Update]"
        }
    }

    private fun showNotification(title: String, text: String, chatId: Long, accountId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("ghostgram://account/$accountId/chat/$chatId")
            putExtra("chatId", chatId)
            putExtra("accountId", accountId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setSubText(GhostPreferences(this, accountId).loadUserSession().let { it.userName.ifBlank { it.phoneNumber } })
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for actual message alerts
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use a unique ID for each chat notification to avoid overwriting
        notificationManager.notify(accountId, (chatId % Int.MAX_VALUE).toInt(), notification)
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
