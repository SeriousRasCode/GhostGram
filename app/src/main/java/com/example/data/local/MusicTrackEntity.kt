package com.example.data.local

import androidx.room.Entity
import com.example.data.model.ChatSourceType
import com.example.data.model.FileAttachment
import com.example.data.model.GhostMessage

@Entity(tableName = "music_tracks", primaryKeys = ["sourceChatId", "telegramChatId", "telegramMsgId"])
data class MusicTrackEntity(
    val sourceChatId: Long,
    val telegramChatId: Long,
    val telegramMsgId: Long,
    val sourceId: Long,
    val sourceTitle: String,
    val sourceAvatarPath: String?,
    val chatType: String,
    val senderId: Long,
    val senderName: String,
    val senderHandle: String,
    val text: String,
    val timestamp: Long,
    val audioPath: String?,
    val attachmentName: String?,
    val attachmentSize: String?,
    val attachmentMime: String?,
    val attachmentLocalPath: String?,
    val attachmentFileId: Int,
    val audioTitle: String?,
    val audioPerformer: String?,
    val audioDurationSeconds: Int,
    val audioCoverFileId: Int
) {
    fun toDomain(): GhostMessage = GhostMessage(
        id = telegramMsgId,
        telegramChatId = telegramChatId,
        sourceId = sourceId,
        sourceTitle = sourceTitle,
        sourceAvatarPath = sourceAvatarPath,
        chatType = runCatching { ChatSourceType.valueOf(chatType) }.getOrDefault(ChatSourceType.GROUP),
        telegramMsgId = telegramMsgId,
        senderId = senderId,
        senderName = senderName,
        senderHandle = senderHandle,
        text = text,
        timestamp = timestamp,
        audioPath = audioPath,
        fileAttachment = attachmentName?.let { name ->
            FileAttachment(name, attachmentSize.orEmpty(), attachmentMime ?: "audio/*",
                localPath = attachmentLocalPath, fileId = attachmentFileId)
        },
        audioTitle = audioTitle,
        audioPerformer = audioPerformer,
        audioDurationSeconds = audioDurationSeconds,
        audioCoverFileId = audioCoverFileId
    )

    companion object {
        fun fromDomain(sourceChatId: Long, message: GhostMessage): MusicTrackEntity = MusicTrackEntity(
            sourceChatId = sourceChatId,
            telegramChatId = message.telegramChatId,
            telegramMsgId = message.telegramMsgId,
            sourceId = message.sourceId,
            sourceTitle = message.sourceTitle,
            sourceAvatarPath = message.sourceAvatarPath,
            chatType = message.chatType.name,
            senderId = message.senderId,
            senderName = message.senderName,
            senderHandle = message.senderHandle,
            text = message.text,
            timestamp = message.timestamp,
            audioPath = message.audioPath,
            attachmentName = message.fileAttachment?.fileName,
            attachmentSize = message.fileAttachment?.fileSizeFormatted,
            attachmentMime = message.fileAttachment?.mimeType,
            attachmentLocalPath = message.fileAttachment?.localPath,
            attachmentFileId = message.fileAttachment?.fileId ?: 0,
            audioTitle = message.audioTitle,
            audioPerformer = message.audioPerformer,
            audioDurationSeconds = message.audioDurationSeconds,
            audioCoverFileId = message.audioCoverFileId
        )
    }
}
