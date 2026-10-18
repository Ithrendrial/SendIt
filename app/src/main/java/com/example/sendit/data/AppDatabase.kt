package com.example.sendit.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sendit.domain.Finding
import com.example.sendit.domain.PoseFrame

@Database(entities = [RouteEntity::class, AttemptEntity::class, PoseFrame::class, Finding::class], version = 2, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    // Provides the database operations used by the repository.
    abstract fun attemptDao(): AttemptDao

    companion object {
        // Adds analysis intervals while preserving existing routes, attempts and reconstructions.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS findings (
                        id TEXT NOT NULL PRIMARY KEY,
                        attemptId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        startFrameIndex INTEGER NOT NULL,
                        endFrameIndex INTEGER NOT NULL,
                        FOREIGN KEY(attemptId) REFERENCES attempts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_findings_attemptId ON findings(attemptId)")
            }
        }
    }
}
