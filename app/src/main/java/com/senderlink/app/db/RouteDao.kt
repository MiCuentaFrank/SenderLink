package com.senderlink.app.db

import androidx.room.*

@Dao
interface RouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<RouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: RouteEntity)

    @Query("SELECT * FROM routes ORDER BY featured DESC, cachedAt DESC LIMIT :limit")
    suspend fun getAll(limit: Int = 50): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE featured = 1 ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getFeatured(limit: Int = 20): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RouteEntity?

    @Query("DELETE FROM routes WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
