package com.senderlink.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrackPointDao {

    @Insert
    suspend fun insert(point: TrackPointEntity)

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: String): List<TrackPointEntity>

    @Query("SELECT COUNT(*) FROM track_points WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    @Query("DELETE FROM track_points WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
