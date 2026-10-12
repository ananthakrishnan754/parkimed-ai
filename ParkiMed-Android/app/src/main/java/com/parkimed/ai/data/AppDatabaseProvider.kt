package com.parkimed.ai.data

import android.content.Context
import androidx.room.Room

object AppDatabaseProvider {
    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "parkimed.db"
            ).build().also { instance = it }
        }
}