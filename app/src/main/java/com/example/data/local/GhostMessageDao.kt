package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GhostMessageDao {
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
