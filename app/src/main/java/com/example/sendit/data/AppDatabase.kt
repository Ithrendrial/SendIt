package com.example.sendit.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AttemptEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attemptDao(): AttemptDao
}
