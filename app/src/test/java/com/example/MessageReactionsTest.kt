package com.example

import com.example.data.remote.TelegramReactionText
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class MessageReactionsTest {
    @Test fun `all emoji custom and paid reactions retain their exact counts`() {
        val emoji = listOf("👍", "❤", "🔥", "🎉", "👏", "😁", "🤔", "🤯", "😱", "😢", "💯", "🙏")
        val list = emoji.mapIndexed { index, value ->
            TdApi.MessageReaction().apply { type = TdApi.ReactionTypeEmoji(value); totalCount = index + 1 }
        } + listOf(
            TdApi.MessageReaction().apply { type = TdApi.ReactionTypeCustomEmoji(123); totalCount = 42 },
            TdApi.MessageReaction().apply { type = TdApi.ReactionTypeCustomEmoji(456); totalCount = 3 },
            TdApi.MessageReaction().apply { type = TdApi.ReactionTypePaid(); totalCount = 999 }
        )
        val reactions = TdApi.MessageReactions().apply { this.reactions = list.toTypedArray() }
        val summary = TelegramReactionText.format(reactions, mapOf(123L to "🦊"))!!
        val chips = summary.split(TelegramReactionText.SEPARATOR)
        assertEquals(15, chips.size)
        emoji.forEachIndexed { index, value -> assertTrue(chips.contains("$value ${index + 1}")) }
        assertTrue(chips.contains("🦊 42"))
        assertTrue(chips.contains("Custom emoji 456 3"))
        assertTrue(chips.contains("⭐ 999"))
        assertNull(TelegramReactionText.format(null, emptyMap()))
    }
}
