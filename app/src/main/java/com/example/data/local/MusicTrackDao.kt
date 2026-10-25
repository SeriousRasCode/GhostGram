package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MusicTrackDao {
    @Query("SELECT * FROM music_tracks WHERE sourceChatId = :sourceChatId ORDER BY timestamp DESC")
    fun getForSource(sourceChatId: Long): List<MusicTrackEntity>

    @Query("DELETE FROM music_tracks WHERE sourceChatId = :sourceChatId")
    fun deleteForSource(sourceChatId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<MusicTrackEntity>): List<Long>

}
