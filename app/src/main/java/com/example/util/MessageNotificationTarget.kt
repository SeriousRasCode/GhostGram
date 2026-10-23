package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.MainActivity
import com.example.data.local.GhostAccounts

data class MessageNotificationTarget(val accountId: String, val chatId: Long, val messageId: Long) {
    fun toIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = Uri.parse("ghostgram://account/$accountId/chat/$chatId/message/$messageId")
        putExtra("accountId", accountId)
        putExtra("chatId", chatId)
        putExtra("messageId", messageId)
    }

    companion object {
        fun fromIntent(intent: Intent?, defaultAccount: String): MessageNotificationTarget? {
            if (intent == null) return null
            val account = intent.getStringExtra("accountId") ?: defaultAccount
            val chat = intent.getLongExtra("chatId", 0)
            val message = intent.getLongExtra("messageId", 0)
            if (!GhostAccounts.isValidId(account) || chat == 0L || message < 0L) return null
            return MessageNotificationTarget(account, chat, message)
        }
    }
}
