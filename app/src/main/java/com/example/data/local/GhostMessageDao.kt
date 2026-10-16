package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GhostMessageDao {

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

    @Query("SELECT * FROM ghost_messages WHERE sourceId = :sourceId ORDER BY timestamp DESC")
    fun getMessagesBySource(sourceId: Long): Flow<List<GhostMessageEntity>>

    @Query("SELECT * FROM ghost_messages WHERE isBookmarked = 1 OR chatType = 'SAVED_MESSAGES' OR sourceId = -1 ORDER BY timestamp DESC")
    fun getBookmarkedMessages(): Flow<List<GhostMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(messages: List<GhostMessageEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: GhostMessageEntity): Long

    @Query("UPDATE ghost_messages SET isBookmarked = :isBookmarked WHERE telegramMsgId = :telegramMsgId")
    fun updateBookmark(telegramMsgId: Long, isBookmarked: Boolean): Int

    @Query("DELETE FROM ghost_messages WHERE sourceId = :sourceId")
    fun clearBySource(sourceId: Long): Int

    @Query("SELECT COUNT(*) FROM ghost_messages")
    fun countAll(): Int

    @Query("SELECT COUNT(*) FROM ghost_messages WHERE chatType = :chatType")
    fun countByChatType(chatType: String): Int
}
