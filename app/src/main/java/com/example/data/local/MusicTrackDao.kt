package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicTrackDao {
    @Query("SELECT * FROM music_tracks WHERE sourceChatId = :sourceChatId ORDER BY timestamp DESC")
    fun getForSource(sourceChatId: Long): List<MusicTrackEntity>

    @Query("SELECT * FROM music_tracks WHERE sourceChatId = :sourceChatId ORDER BY timestamp DESC")
    fun observeForSource(sourceChatId: Long): Flow<List<MusicTrackEntity>>

    @Query("UPDATE music_tracks SET isFavorite = :isFavorite WHERE telegramChatId = :chatId AND telegramMsgId = :messageId")
    fun setFavorite(chatId: Long, messageId: Long, isFavorite: Boolean): Int

    @Query("UPDATE music_tracks SET listenedMs = listenedMs + :listenedMs, lastPlayedAt = :playedAt WHERE telegramChatId = :chatId AND telegramMsgId = :messageId")
    fun recordListeningTime(chatId: Long, messageId: Long, listenedMs: Long, playedAt: Long): Int

    @Query("DELETE FROM music_tracks WHERE sourceChatId = :sourceChatId")
    fun deleteForSource(sourceChatId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<MusicTrackEntity>): List<Long>

}
