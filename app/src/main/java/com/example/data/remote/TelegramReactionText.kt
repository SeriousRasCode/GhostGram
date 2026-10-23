package com.example.data.remote

import org.drinkless.tdlib.TdApi

internal object TelegramReactionText {
    const val SEPARATOR = " · "
    fun format(reactions: TdApi.MessageReactions?, customEmoji: Map<Long, String>): String? {
        if (reactions == null || reactions.reactions.isEmpty()) return null
        return reactions.reactions.joinToString(SEPARATOR) { reaction ->
            val label = when (val type = reaction.type) {
                is TdApi.ReactionTypeEmoji -> type.emoji
                is TdApi.ReactionTypeCustomEmoji -> customEmoji[type.customEmojiId] ?: "Custom emoji ${type.customEmojiId}"
                is TdApi.ReactionTypePaid -> "⭐"
                else -> "Reaction"
            }
            "$label ${reaction.totalCount}"
        }
    }
}
