package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSourceDao {
    @Query("SELECT * FROM focus_sources WHERE telegramChatId = :chatId LIMIT 1")
    fun findSourceByTelegramChatId(chatId: Long): FocusSourceEntity?

    @Query("SELECT * FROM focus_sources ORDER BY lastMessageTime DESC")
    fun getAllSources(): Flow<List<FocusSourceEntity>>

    @Query("SELECT * FROM focus_sources WHERE type = :type ORDER BY lastMessageTime DESC")
    fun getSourcesByType(type: String): Flow<List<FocusSourceEntity>>

    @Query("SELECT * FROM focus_sources WHERE id = :id")
    fun getSourceById(id: Long): Flow<FocusSourceEntity?>

    @Query("SELECT id FROM focus_sources WHERE telegramChatId = :chatId LIMIT 1")
    fun findByTelegramChatId(chatId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSource(source: FocusSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(sources: List<FocusSourceEntity>): List<Long>

    @Query("DELETE FROM focus_sources WHERE id = :id")
    fun deleteSource(id: Long): Int

    @Query("UPDATE focus_sources SET lastMessageText = :text, lastMessageTime = :time WHERE id = :id AND lastMessageTime <= :time")
    fun updateLastMessage(id: Long, text: String, time: Long): Int

    @Query("UPDATE focus_sources SET avatarPath = :path WHERE id = :id")
    fun updateAvatar(id: Long, path: String): Int

    @Query("UPDATE focus_sources SET telegramChatId = :chatId WHERE id = :id")
    fun updateTelegramChatId(id: Long, chatId: Long): Int

    @Query("SELECT COUNT(*) FROM focus_sources WHERE type = :type")
    fun countByType(type: String): Int
}
