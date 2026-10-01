package com.example.sendit.data

import org.junit.Test
import kotlinx.coroutines.runBlocking

class AttemptDaoTest {
    @Test
    fun insertAttempt_shouldReturnGeneratedId() = runBlocking {
        // Checks insertion API surface: create an `AttemptEntity` and call `AttemptDao.insert`.
        val attempt = AttemptEntity("attempt-1", "route-1", 0L, "Fall", "")
        // AttemptDao.insert is a suspend function (the implementation will provide a DAO.)
        val dao: AttemptDao = TODO("Provide AttemptDao implementation")
        @Suppress("UNUSED_VARIABLE")
        val id = dao.insert(attempt)
    }
}
