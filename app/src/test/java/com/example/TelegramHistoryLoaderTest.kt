package com.example

import com.example.data.remote.TelegramHistoryLoader
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class TelegramHistoryLoaderTest {
    private fun message(id: Long) = TdApi.Message().apply { this.id = id }
    private fun page(vararg ids: Long) = TdApi.Messages(ids.size, ids.map(::message).toTypedArray())

    @Test fun `short pages are published immediately and do not signal end of history`() = runTest {
        val requests = mutableListOf<Long>()
        val offsets = mutableListOf<Int>()
        val published = mutableListOf<List<Long>>()
        TelegramHistoryLoader.load(123, minimumMessages = 4, fetch = {
            requests += it.fromMessageId
            offsets += it.offset
            when (it.fromMessageId) { 0L -> page(10); 10L -> page(10, 9, 8); else -> page(8, 7) }
        }, publish = { published += it.map { message -> message.id } })
        assertEquals(listOf(0L, 10L, 8L), requests)
        assertEquals(listOf(-1, 0, 0), offsets)
        assertEquals(listOf(listOf(10L), listOf(9L, 8L), listOf(7L)), published)
    }

    @Test fun `catch-up crosses multiple pages until the cached boundary`() = runTest {
        val ids = mutableListOf<Long>()
        val offsets = mutableListOf<Int>()
        TelegramHistoryLoader.load(123, minimumMessages = 2, stopAtMessageId = 3, fetch = {
            offsets += it.offset
            when (it.fromMessageId) { 0L -> page(9, 8); 8L -> page(8, 7, 6); else -> page(6, 3, 2) }
        }, publish = { ids += it.map { message -> message.id } })
        assertEquals(listOf(9L, 8L, 7L, 6L, 3L, 2L), ids)
        assertEquals(listOf(-1, 0, 0), offsets)
    }

    @Test fun `duplicate anchor page terminates without looping`() = runTest {
        var calls = 0
        val ids = mutableListOf<Long>()
        TelegramHistoryLoader.load(123, fetch = { calls++; page(5) }, publish = { ids += it.map { message -> message.id } })
        assertEquals(2, calls)
        assertEquals(listOf(5L), ids)
    }

    @Test fun `network failure propagates after already publishing the first page`() = runTest {
        var published = false
        try {
            TelegramHistoryLoader.load(123, fetch = {
                if (it.fromMessageId == 0L) page(5) else throw IllegalStateException("offline")
            }, publish = { published = true })
            fail("Expected a retriable failure")
        } catch (expected: IllegalStateException) { assertEquals("offline", expected.message) }
        assertTrue(published)
    }
}
