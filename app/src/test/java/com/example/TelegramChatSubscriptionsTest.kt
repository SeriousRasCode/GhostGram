package com.example

import com.example.data.remote.TelegramChatSubscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TelegramChatSubscriptionsTest {
    @Test fun `closing detail or discussion keeps feed subscribed`() = runTest {
        val opened = mutableListOf<Long>()
        val closed = mutableListOf<Long>()
        val subscriptions = TelegramChatSubscriptions({ opened.add(it); Unit }, { closed.add(it); Unit })
        subscriptions.replace("feed", setOf(1, 2))
        subscriptions.replace("detail", setOf(1))
        subscriptions.replace("discussion", setOf(1))
        subscriptions.replace("detail", emptySet())
        subscriptions.replace("discussion", emptySet())
        assertEquals(listOf(1L, 2L), opened)
        assertTrue(closed.isEmpty())
        subscriptions.replace("feed", setOf(2))
        assertEquals(listOf(1L), closed)
    }

    @Test fun `failed subscription can retry without reopening successful chats`() = runTest {
        val opened = mutableListOf<Long>()
        var failOnce = true
        val subscriptions = TelegramChatSubscriptions({ id ->
            if (id == 2L && failOnce) { failOnce = false; throw IllegalStateException("offline") }
            opened.add(id)
            Unit
        }, {})
        try { subscriptions.replace("feed", linkedSetOf(1, 2)); fail("Expected failure") }
        catch (_: IllegalStateException) { }
        subscriptions.replace("feed", linkedSetOf(1, 2))
        assertEquals(listOf(1L, 2L), opened)
    }

    @Test fun `reset reopens subscriptions after a new login`() = runTest {
        var opens = 0
        val subscriptions = TelegramChatSubscriptions({ opens++; Unit }, {})
        subscriptions.replace("feed", setOf(1))
        subscriptions.reset()
        subscriptions.replace("feed", setOf(1))
        assertEquals(2, opens)
    }
}
