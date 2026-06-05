package com.senderlink.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_points")
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val lat: Double,
    val lng: Double,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
