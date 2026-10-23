package com.example

import com.example.data.remote.TelegramUpdateBus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class TelegramUpdateBusTest {
    @Test fun `slow subscribers retain all mutations in order beyond buffer capacity`() = runTest {
        val bus = TelegramUpdateBus()
        val gate = CompletableDeferred<Unit>()
        val received = mutableListOf<Long>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.updates.collect { update ->
                gate.await()
                received += (update as TdApi.UpdateDeleteMessages).messageIds.single()
            }
        }
        val fastSubscriber = mutableListOf<Long>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.updates.collect { fastSubscriber += (it as TdApi.UpdateDeleteMessages).messageIds.single() }
        }
        repeat(100) { index ->
            bus.offer(TdApi.UpdateDeleteMessages().apply { messageIds = longArrayOf(index.toLong()); isPermanent = true })
        }
        runCurrent()
        assertTrue(received.isEmpty())
        assertEquals((0L until 100L).toList(), fastSubscriber)
        gate.complete(Unit)
        runCurrent()
        assertEquals((0L until 100L).toList(), received)
    }

    @Test fun `file progress is filtered but completion reaches storage listeners`() = runTest {
        val bus = TelegramUpdateBus()
        val received = mutableListOf<TdApi.Object>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { bus.updates.collect { received += it } }
        fun update(completed: Boolean) = TdApi.UpdateFile(TdApi.File().apply {
            id = 7
            local = TdApi.LocalFile().apply { isDownloadingCompleted = completed }
        })
        repeat(1000) { bus.offer(update(false)) }
        val complete = update(true)
        bus.offer(complete)
        runCurrent()
        assertEquals(listOf(complete), received)
    }
}
