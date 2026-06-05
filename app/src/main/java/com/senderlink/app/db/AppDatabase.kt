package com.senderlink.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackPointEntity::class, RouteEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackPointDao(): TrackPointDao
    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "senderlink_db"
                )
                // TrackPoint y Route son solo caché local: perder datos en un cambio de
                // esquema es aceptable (se re-sincronizan desde el servidor).
                // En producción con datos de usuario valiosos, usar addMigrations(...).
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
