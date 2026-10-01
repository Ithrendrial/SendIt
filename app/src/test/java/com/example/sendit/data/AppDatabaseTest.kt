package com.example.sendit.data

import org.junit.Test
import org.junit.Assert.assertNotNull

class AppDatabaseTest {
    @Test
    fun appDatabase_shouldExposeAttemptDao() {
        // Checks that `AppDatabase` exposes an `AttemptDao` accessor.
        val db: AppDatabase = TODO("Provide AppDatabase implementation")
        val dao = db.attemptDao()
        assertNotNull(dao)
    }
}
