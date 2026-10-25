package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface MusicTrackDao {
    @Query("SELECT * FROM music_tracks WHERE sourceChatId = :sourceChatId ORDER BY timestamp DESC")
    suspend fun getForSource(sourceChatId: Long): List<MusicTrackEntity>

    @Query("DELETE FROM music_tracks WHERE sourceChatId = :sourceChatId")
    suspend fun deleteForSource(sourceChatId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<MusicTrackEntity>)

    @Transaction
    suspend fun replaceForSource(sourceChatId: Long, tracks: List<MusicTrackEntity>) {
        deleteForSource(sourceChatId)
        if (tracks.isNotEmpty()) insertAll(tracks)
    }
}
