package com.example.data.remote

import org.drinkless.tdlib.TdApi

/** TDLib may return a short page before reaching the end; publish it, then keep paging. */
internal object TelegramHistoryLoader {
    suspend fun load(
        chatId: Long,
        fromMessageId: Long = 0,
        minimumMessages: Int = 50,
        pageSize: Int = 50,
        stopAtMessageId: Long = 0,
        fetch: suspend (TdApi.GetChatHistory) -> TdApi.Messages,
        publish: suspend (List<TdApi.Message>) -> Unit
    ) {
        var cursor = fromMessageId
        var count = 0
        while (true) {
            // TDLib interprets fromMessageId=0 as the oldest history, not the newest.
            // Start at the latest message, then page toward older messages as needed.
            val raw = fetch(TdApi.GetChatHistory(chatId, cursor, if (cursor == 0L) -1 else 0, pageSize, false)).messages.filterNotNull()
            val page = raw.distinctBy { it.id }.filter { cursor == 0L || it.id < cursor }
            if (page.isEmpty()) return
            publish(page)
            count += page.size
            cursor = page.minOf { it.id }
            if (count >= minimumMessages && (stopAtMessageId == 0L || cursor <= stopAtMessageId)) return
        }
    }
}
