package com.example.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/** Keep message mutations ordered without blocking TDLib or dropping edits/deletions. */
internal class TelegramUpdateBus(
    capacity: Int = 1024,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val events = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = capacity)
    private val pending = Channel<TdApi.Object>(Channel.UNLIMITED)
    val updates = events.asSharedFlow()
    private val recovery = MutableStateFlow(0L)
    val recoveryVersion = recovery.asStateFlow()

    init { scope.launch { for (update in pending) events.emit(update) } }

    fun offer(update: TdApi.Object) {
        // Download progress can arrive hundreds of times per second. Only completion changes storage.
        if (update is TdApi.UpdateFile && !update.file.local.isDownloadingCompleted) return
        when (update) {
            is TdApi.UpdateNewMessage, is TdApi.UpdateChatLastMessage,
            is TdApi.UpdateMessageContent, is TdApi.UpdateMessageInteractionInfo,
            is TdApi.UpdateDeleteMessages, is TdApi.UpdateFile -> pending.trySend(update)
        }
    }

    fun requestRecovery() { recovery.update { it + 1 } }
}
