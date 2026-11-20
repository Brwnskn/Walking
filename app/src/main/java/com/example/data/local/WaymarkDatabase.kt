package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TripEntity::class], version = 1, exportSchema = false)
abstract class WaymarkDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var instance: WaymarkDatabase? = null

        fun getInstance(context: Context): WaymarkDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WaymarkDatabase::class.java,
                    "waymark_trips.db",
                ).build().also { instance = it }
            }
        }
    }
}
