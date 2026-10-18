package com.example.sendit.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppDatabaseTest {
    @Test
    fun appDatabase_exposesWorkingAttemptDao() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            // Opening the database also validates the generated Room schema.
            db.openHelper.writableDatabase
            assertNotNull(db.attemptDao())
        } finally {
            db.close()
        }
    }
}
