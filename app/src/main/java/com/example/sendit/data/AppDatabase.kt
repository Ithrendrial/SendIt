package com.example.sendit.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.sendit.domain.PoseFrame

@Database(entities = [RouteEntity::class, AttemptEntity::class, PoseFrame::class], version = 1, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    // Provides the database operations used by the repository.
    abstract fun attemptDao(): AttemptDao
}
