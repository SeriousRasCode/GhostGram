package com.example.data.remote

import org.drinkless.tdlib.TdApi

internal object TelegramHistoryRequests {
    // A small nonzero ID and negative offset locate the first accessible message,
    // including chats whose original first messages have been deleted.
    fun first(chatId: Long) = TdApi.GetChatHistory(chatId, 1, -1, 1, false)
    fun around(chatId: Long, messageId: Long) = TdApi.GetChatHistory(chatId, messageId, -25, 50, false)
    fun newer(chatId: Long, messageId: Long) = TdApi.GetChatHistory(chatId, messageId, -49, 50, false)
    fun older(chatId: Long, messageId: Long) = TdApi.GetChatHistory(chatId, messageId, 0, 50, false)
    fun latest(chatId: Long) = TdApi.GetChatHistory(chatId, 0, 0, 50, false)
}
