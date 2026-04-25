package com.senderlink.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val json: String,          // Route serializada como JSON
    val featured: Int = 0,     // 1 si es destacada
    val cachedAt: Long = System.currentTimeMillis()
)
