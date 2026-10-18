package com.example.sendit.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AttemptDaoTest {
    @Test
    fun insertAttempt_preservesItsAssignedIdAndDetails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.attemptDao()
            dao.insertRoute(RouteEntity("route-1", "Test route", "V1", "Test gym"))
            val attempt = AttemptEntity("attempt-1", "route-1", 0L, "Fall", "Test notes")

            dao.insert(attempt)

            assertEquals(attempt, dao.getById(attempt.id))
        } finally {
            db.close()
        }
    }
}
