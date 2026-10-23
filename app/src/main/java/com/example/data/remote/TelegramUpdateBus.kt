package com.example.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.Channel
import org.drinkless.tdlib.TdApi

/** Keep message mutations ordered without blocking TDLib or dropping edits/deletions. */
internal class TelegramUpdateBus {
    private val subscribers = mutableSetOf<Channel<TdApi.Object>>()
    // Independent queues prevent a slow notification/discussion collector from delaying storage.
    val updates = flow {
        val pending = Channel<TdApi.Object>(Channel.UNLIMITED)
        synchronized(subscribers) { subscribers.add(pending) }
        try { for (update in pending) emit(update) }
        finally {
            synchronized(subscribers) { subscribers.remove(pending) }
            pending.cancel()
        }
    }
    private val recovery = MutableStateFlow(0L)
    val recoveryVersion = recovery.asStateFlow()

    fun offer(update: TdApi.Object) {
        // Download progress can arrive hundreds of times per second. Only completion changes storage.
        if (update is TdApi.UpdateFile && !update.file.local.isDownloadingCompleted) return
        when (update) {
            is TdApi.UpdateNewMessage, is TdApi.UpdateChatLastMessage,
            is TdApi.UpdateMessageContent, is TdApi.UpdateMessageInteractionInfo,
            is TdApi.UpdateDeleteMessages, is TdApi.UpdateFile -> synchronized(subscribers) {
                subscribers.forEach { it.trySend(update) }
            }
        }
    }

    fun requestRecovery() { recovery.update { it + 1 } }
}
