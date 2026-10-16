package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ChatSourceType
import com.example.data.model.FileAttachment
import com.example.data.model.GhostMessage

@Entity(tableName = "ghost_messages")
data class GhostMessageEntity(
    @PrimaryKey
    val telegramMsgId: Long,
    val sourceId: Long = 0,
    val sourceTitle: String = "",
    val sourceAvatarPath: String? = null,
    val chatType: String, // "CHANNEL", "GROUP", "SAVED_MESSAGES", "PERSONAL"
    val senderId: Long = 0,
    val senderName: String,
    val senderHandle: String,
    val text: String,
    val timestamp: Long,
    val imageUrl: String? = null,
    val stickerPath: String? = null,
    val videoPath: String? = null,
    val audioPath: String? = null,
    val voicePath: String? = null,
    val attachmentName: String? = null,
    val attachmentSize: String? = null,
    val attachmentMime: String? = null,
    val attachmentUrl: String? = null,
    val attachmentLocalPath: String? = null,
    val attachmentFileId: Int = 0,
    val isPinned: Boolean = false,
    val views: String? = null,
    val reactions: String? = null,
    val isBookmarked: Boolean = false,
    val isUrgent: Boolean = false
) {
    fun toDomain(): GhostMessage {
        val fileAttachment = if (!attachmentName.isNullOrBlank()) {
            FileAttachment(
                fileName = attachmentName,
                fileSizeFormatted = attachmentSize ?: "0 B",
                mimeType = attachmentMime ?: "application/octet-stream",
                downloadUrl = attachmentUrl,
                localPath = attachmentLocalPath,
                fileId = attachmentFileId
            )
        } else null

        val domainChatType = when (chatType) {
            "GROUP" -> ChatSourceType.GROUP
            "SAVED_MESSAGES" -> ChatSourceType.SAVED_MESSAGES
            "PERSONAL" -> ChatSourceType.PERSONAL
            else -> ChatSourceType.CHANNEL
        }

        return GhostMessage(
            id = telegramMsgId,
            sourceId = sourceId,
            sourceTitle = sourceTitle,
            sourceAvatarPath = sourceAvatarPath,
            chatType = domainChatType,
            telegramMsgId = telegramMsgId,
            senderId = senderId,
            senderName = senderName,
            senderHandle = senderHandle,
            text = text,
            timestamp = timestamp,
            imageUrl = imageUrl,
            stickerPath = stickerPath,
            videoPath = videoPath,
            audioPath = audioPath,
            voicePath = voicePath,
            fileAttachment = fileAttachment,
            isPinned = isPinned,
            views = views,
            reactions = reactions,
            isBookmarked = isBookmarked,
            isUrgent = isUrgent
        )
    }

    companion object {
        fun fromDomain(domain: GhostMessage): GhostMessageEntity {
            return GhostMessageEntity(
                telegramMsgId = domain.telegramMsgId,
                sourceId = domain.sourceId,
                sourceTitle = domain.sourceTitle,
                sourceAvatarPath = domain.sourceAvatarPath,
                chatType = domain.chatType.name,
                senderId = domain.senderId,
                senderName = domain.senderName,
                senderHandle = domain.senderHandle,
                text = domain.text,
                timestamp = domain.timestamp,
                imageUrl = domain.imageUrl,
                stickerPath = domain.stickerPath,
                videoPath = domain.videoPath,
                audioPath = domain.audioPath,
                voicePath = domain.voicePath,
                attachmentName = domain.fileAttachment?.fileName,
                attachmentSize = domain.fileAttachment?.fileSizeFormatted,
                attachmentMime = domain.fileAttachment?.mimeType,
                attachmentUrl = domain.fileAttachment?.downloadUrl,
                attachmentLocalPath = domain.fileAttachment?.localPath,
                attachmentFileId = domain.fileAttachment?.fileId ?: 0,
                isPinned = domain.isPinned,
                views = domain.views,
                reactions = domain.reactions,
                isBookmarked = domain.isBookmarked,
                isUrgent = domain.isUrgent
            )
        }
    }
}
