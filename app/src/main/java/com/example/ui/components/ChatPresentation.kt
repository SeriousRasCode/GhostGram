package com.example.ui.components

import com.example.data.model.ChatSourceType
import com.example.data.model.GhostMessage
import java.util.Calendar
import java.util.TimeZone

internal fun sameMessageDay(first: Long, second: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
    val a = Calendar.getInstance(zone).apply { timeInMillis = first }
    val b = Calendar.getInstance(zone).apply { timeInMillis = second }
    return a.get(Calendar.ERA) == b.get(Calendar.ERA) && a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

internal fun messagesBelongTogether(older: GhostMessage?, newer: GhostMessage?, zone: TimeZone = TimeZone.getDefault()): Boolean {
    if (older == null || newer == null || newer.chatType == ChatSourceType.CHANNEL) return false
    val sameSender = if (older.senderId != 0L || newer.senderId != 0L) older.senderId == newer.senderId
        else older.senderName == newer.senderName
    return sameSender && older.telegramChatId == newer.telegramChatId && older.sourceId == newer.sourceId &&
        older.chatType == newer.chatType && newer.timestamp - older.timestamp in 0..300_000 &&
        sameMessageDay(older.timestamp, newer.timestamp, zone)
}

/** Refreshing the latest comments must retain already-loaded history and update changed entries. */
internal fun mergeDiscussionMessages(existing: List<GhostMessage>, page: List<GhostMessage>): List<GhostMessage> =
    (existing.associateBy { it.telegramChatId to it.telegramMsgId } +
        page.associateBy { it.telegramChatId to it.telegramMsgId }).values.sortedByDescending { it.telegramMsgId }
