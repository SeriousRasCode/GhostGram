package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GhostMessageDao {
    @Query("SELECT ghost_messages.* FROM ghost_messages INNER JOIN focus_sources ON ghost_messages.sourceId = focus_sources.id WHERE stickerFileId = 0 AND (stickerPath IS NOT NULL OR text LIKE 'Sticker %' OR text = '[Sticker]')")
    fun legacyStickerMessages(): List<GhostMessageEntity>

    @Query("UPDATE ghost_messages SET stickerFileId = :fileId, stickerFormat = :format, stickerEmoji = :emoji, stickerPath = :path, stickerThumbnailFileId = :thumbnailId, stickerThumbnailPath = :thumbnailPath WHERE sourceId = :sourceId AND telegramMsgId = :messageId")
    fun updateStickerMetadata(sourceId: Long, messageId: Long, fileId: Int, format: String, emoji: String, path: String?, thumbnailId: Int, thumbnailPath: String?): Int

    @Query("UPDATE ghost_messages SET stickerPath = :path WHERE stickerFileId = :fileId")
    fun updateSticker(fileId: Int, path: String): Int

    @Query("UPDATE ghost_messages SET stickerThumbnailPath = :path WHERE stickerThumbnailFileId = :fileId")
    fun updateStickerThumbnail(fileId: Int, path: String): Int
    @Query("UPDATE ghost_messages SET reactions = :reactions, views = :views, commentCount = :comments WHERE sourceId = :sourceId AND telegramMsgId = :messageId")
    fun updateInteractions(sourceId: Long, messageId: Long, reactions: String?, views: String?, comments: Int): Int
    @Query("SELECT * FROM ghost_messages WHERE sourceId = :sourceId AND telegramMsgId = :messageId LIMIT 1")
    fun find(sourceId: Long, messageId: Long): GhostMessageEntity?

    @Query("DELETE FROM ghost_messages WHERE sourceId = :sourceId AND telegramMsgId IN (:ids)")
    fun deleteMessages(sourceId: Long, ids: List<Long>): Int

    @Query("UPDATE ghost_messages SET imageUrl = :path WHERE photoFileId = :fileId")
    fun updatePhoto(fileId: Int, path: String): Int

    @Query("UPDATE ghost_messages SET attachmentLocalPath = :path WHERE attachmentFileId = :fileId")
    fun updateAttachment(fileId: Int, path: String): Int

    @Query("SELECT * FROM ghost_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<GhostMessageEntity>>

    @Query("SELECT * FROM ghost_messages WHERE chatType IN ('GROUP', 'CHANNEL') ORDER BY timestamp DESC")
    fun getFeedMessages(): Flow<List<GhostMessageEntity>>

    @Query("SELECT * FROM ghost_messages WHERE chatType IN ('GROUP', 'CHANNEL', 'PERSONAL') ORDER BY timestamp DESC")
    fun getUniversalFeedMessages(): Flow<List<GhostMessageEntity>>

    @Query("SELECT * FROM ghost_messages WHERE chatType = 'PERSONAL' ORDER BY timestamp DESC")
    fun getPersonalMessages(): Flow<List<GhostMessageEntity>>

    @Query("SELECT * FROM ghost_messages WHERE chatType = :chatType ORDER BY timestamp DESC")
    fun getMessages(chatType: String): Flow<List<GhostMessageEntity>>

    @Query("SELECT * FROM ghost_messages WHERE sourceId = :sourceId ORDER BY telegramMsgId DESC")
    fun getMessagesBySource(sourceId: Long): Flow<List<GhostMessageEntity>>

    @Query("SELECT * FROM ghost_messages WHERE isBookmarked = 1 OR chatType = 'SAVED_MESSAGES' OR sourceId = -1 ORDER BY timestamp DESC")
    fun getBookmarkedMessages(): Flow<List<GhostMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(messages: List<GhostMessageEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: GhostMessageEntity): Long

    @Query("UPDATE ghost_messages SET isBookmarked = :isBookmarked WHERE sourceId = :sourceId AND telegramMsgId = :telegramMsgId")
    fun updateBookmark(sourceId: Long, telegramMsgId: Long, isBookmarked: Boolean): Int

    @Query("DELETE FROM ghost_messages WHERE sourceId = :sourceId")
    fun clearBySource(sourceId: Long): Int

    @Query("SELECT COUNT(*) FROM ghost_messages")
    fun countAll(): Int

    @Query("SELECT COUNT(*) FROM ghost_messages WHERE chatType = :chatType")
    fun countByChatType(chatType: String): Int
}
