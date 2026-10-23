package com.example.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Feed, detail and discussion owners must not close one another's TDLib subscription. */
internal class TelegramChatSubscriptions(
    private val open: suspend (Long) -> Unit,
    private val close: suspend (Long) -> Unit
) {
    private val mutex = Mutex()
    private val owners = mutableMapOf<String, Set<Long>>()
    private val opened = mutableSetOf<Long>()

    suspend fun replace(owner: String, ids: Set<Long>) = mutex.withLock {
        owners[owner] = ids.filter { it != 0L }.toSet()
        val desired = owners.values.flatten().toSet()
        // Update only after successful requests, so a failure can be retried.
        (desired - opened).forEach { id -> open(id); opened.add(id) }
        (opened - desired).forEach { id -> close(id); opened.remove(id) }
        if (ids.isEmpty()) owners.remove(owner)
    }

    suspend fun reset() = mutex.withLock { owners.clear(); opened.clear() }
}
