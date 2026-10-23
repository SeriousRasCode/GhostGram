package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ChatHistoryWindow
import com.example.data.remote.TelegramHistoryRequests
import com.example.util.MessageNotificationTarget
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MessageNavigationTest {
    @Test fun `notifications retain exact long message IDs across chat types and accounts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accounts = listOf("default", "11111111-1111-1111-1111-111111111111")
        val intents = accounts.flatMap { account ->
            listOf(123456789L, -123456789L, -1001234567890L).map { chat ->
                val target = MessageNotificationTarget(account, chat, 9876543210L shl 20)
                val intent = target.toIntent(context)
                assertEquals(target, MessageNotificationTarget.fromIntent(intent, "default"))
                intent.data.toString()
            }
        }
        assertEquals(6, intents.distinct().size)
        val first = MessageNotificationTarget("default", 42, 1L shl 20).toIntent(context)
        val second = MessageNotificationTarget("default", 42, 2L shl 20).toIntent(context)
        assertFalse(first.filterEquals(second))
    }

    @Test fun `legacy notifications open latest and unrelated intents do not navigate`() {
        val legacy = Intent().putExtra("chatId", -100L)
        assertEquals(MessageNotificationTarget("default", -100, 0), MessageNotificationTarget.fromIntent(legacy, "default"))
        assertNull(MessageNotificationTarget.fromIntent(Intent(), "default"))
        assertNull(MessageNotificationTarget.fromIntent(legacy.putExtra("accountId", "../invalid"), "default"))
    }

    @Test fun `oldest and latest queries differ and jump windows exclude disconnected cached pages`() {
        val first = TelegramHistoryRequests.first(100)
        assertTrue(first.fromMessageId > 0)
        assertTrue(first.offset < 0)
        assertFalse(first.onlyLocal)
        assertEquals(0L, TelegramHistoryRequests.latest(100).fromMessageId)
        val around = TelegramHistoryRequests.around(100, 500)
        assertEquals(500L, around.fromMessageId)
        assertTrue(around.offset < 0)
        assertTrue(around.limit > -around.offset)
        val window = ChatHistoryWindow(400, 600)
        assertEquals(listOf(400L, 500L, 600L), listOf(1L, 400L, 500L, 600L, 9999L).filter(window::contains))
        assertTrue(window.canLoadNewer)
        assertFalse(ChatHistoryWindow(400, Long.MAX_VALUE).canLoadNewer)
    }
}
