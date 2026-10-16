package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ChatSourceType
import com.example.data.model.FocusSource

@Entity(tableName = "focus_sources")
data class FocusSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "CHANNEL" or "GROUP"
    val title: String,
    val handleOrId: String,
    val description: String = "",
    val avatarColorHex: String = "#0284C7",
    val avatarPath: String? = null,
    val unreadCount: Int = 0,
    val lastMessageText: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val isMuted: Boolean = false,
    val telegramChatId: Long = 0
) {
    fun toDomain(): FocusSource {
        return FocusSource(
            id = id,
            type = try { ChatSourceType.valueOf(type) } catch (e: Exception) { ChatSourceType.CHANNEL },
            title = title,
            handleOrId = handleOrId,
            description = description,
            avatarColorHex = avatarColorHex,
            avatarPath = avatarPath,
            unreadCount = unreadCount,
            lastMessageText = lastMessageText,
            lastMessageTime = lastMessageTime,
            isMuted = isMuted,
            telegramChatId = telegramChatId
        )
    }

    companion object {
        fun fromDomain(domain: FocusSource): FocusSourceEntity {
            return FocusSourceEntity(
                id = domain.id,
                type = domain.type.name,
                title = domain.title,
                handleOrId = domain.handleOrId,
                description = domain.description,
                avatarColorHex = domain.avatarColorHex,
                avatarPath = domain.avatarPath,
                unreadCount = domain.unreadCount,
                lastMessageText = domain.lastMessageText,
                lastMessageTime = domain.lastMessageTime,
                isMuted = domain.isMuted,
                telegramChatId = domain.telegramChatId
            )
        }
    }
}
