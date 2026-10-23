package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.data.model.ChatSourceType
import com.example.data.model.GhostMessage
import com.example.ui.components.mergeDiscussionMessages
import com.example.ui.components.messagesBelongTogether
import com.example.ui.components.sameMessageDay
import com.example.ui.theme.DarkColorScheme
import com.example.ui.theme.LightColorScheme
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class ChatPresentationTest {
    private fun message(id: Long, time: Long = 1_700_000_000_000L) = GhostMessage(id = id, sourceId = 1,
        telegramMsgId = id, telegramChatId = -100, chatType = ChatSourceType.GROUP, senderId = 12,
        senderName = "Maya", senderHandle = "", text = "Message $id", timestamp = time)

    @Test fun `nearby messages group only for the same sender and chat`() {
        val first = message(1)
        val next = message(2, first.timestamp + 60_000)
        assertTrue(messagesBelongTogether(first, next))
        assertFalse(messagesBelongTogether(first, next.copy(senderId = 13)))
        assertFalse(messagesBelongTogether(first, next.copy(telegramChatId = -200)))
        assertFalse(messagesBelongTogether(first, next.copy(timestamp = first.timestamp + 300_001)))
        assertFalse(messagesBelongTogether(first.copy(chatType = ChatSourceType.CHANNEL), next.copy(chatType = ChatSourceType.CHANNEL)))
    }

    @Test fun `date boundaries follow the local timezone`() {
        val before = 1_704_153_599_000L // 2024-01-01 23:59:59 UTC
        val after = before + 2_000
        assertFalse(sameMessageDay(before, after, TimeZone.getTimeZone("UTC")))
        assertTrue(sameMessageDay(before, after, TimeZone.getTimeZone("Africa/Nairobi")))
        assertFalse(messagesBelongTogether(message(1, before), message(2, after), TimeZone.getTimeZone("UTC")))
    }

    @Test fun `comment refresh updates entries while retaining older loaded pages`() {
        val previous = listOf(message(8), message(3), message(2))
        val merged = mergeDiscussionMessages(previous, listOf(message(9), message(8).copy(text = "Edited")))
        assertEquals(listOf(9L, 8L, 3L, 2L), merged.map { it.telegramMsgId })
        assertEquals("Edited", merged[1].text)
    }

    @Test fun `comment identities remain separate across chats`() {
        val merged = mergeDiscussionMessages(listOf(message(1)), listOf(message(1).copy(telegramChatId = -200)))
        assertEquals(2, merged.size)
    }

    @Test fun `message text metadata and accents meet normal text contrast in both themes`() {
        fun contrast(a: Color, b: Color): Float = (maxOf(a.luminance(), b.luminance()) + 0.05f) /
            (minOf(a.luminance(), b.luminance()) + 0.05f)
        listOf(LightColorScheme, DarkColorScheme).forEach { colors ->
            listOf(colors.surface, colors.secondaryContainer).forEach { background ->
                listOf(colors.onSurface, colors.onSurfaceVariant, colors.primary).forEach { foreground ->
                    assertTrue("Text contrast ${contrast(foreground, background)} is below 4.5:1", contrast(foreground, background) >= 4.5f)
                }
            }
            assertTrue(contrast(colors.onPrimaryContainer, colors.primaryContainer) >= 4.5f)
        }
    }
}
