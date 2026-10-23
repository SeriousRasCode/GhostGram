package com.example.data.model

enum class ChatHistoryDirection { FIRST, LATEST, AROUND, OLDER, NEWER }

data class ChatHistoryWindow(val oldestMessageId: Long, val newestMessageId: Long) {
    fun contains(id: Long) = id in oldestMessageId..newestMessageId
    val canLoadNewer: Boolean get() = newestMessageId != Long.MAX_VALUE
}

data class ChatMessageJump(val requestId: Long, val sourceId: Long, val messageId: Long, val highlight: Boolean = false)
