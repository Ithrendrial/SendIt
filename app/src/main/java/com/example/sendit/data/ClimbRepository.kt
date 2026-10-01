package com.example.sendit.data

import androidx.room.withTransaction
import com.example.sendit.domain.PoseFrame

class ClimbRepository(private val database: AppDatabase) {
    private val dao = database.attemptDao()

    // Observes saved data so playback uses the database as its source of truth.
    fun observeAttempt(id: String) = dao.observeAttempt(id)

    // Reads an existing attempt when a worker resumes after interruption.
    suspend fun getAttempt(id: String) = dao.getById(id)

    // Saves the route and its new attempt together.
    suspend fun saveRoute(route: RouteEntity, attempt: AttemptEntity) = database.withTransaction {
        dao.insertRoute(route)
        dao.insert(attempt)
    }

    // Saves a complete reconstruction before marking the attempt ready to display.
    suspend fun saveAnalysis(attemptId: String, frames: List<PoseFrame>, aspectRatio: Float) = database.withTransaction {
        dao.deleteFrames(attemptId)
        dao.insertFrames(frames)
        dao.complete(attemptId, aspectRatio)
    }

    // Clears the processing flag when extraction fails.
    suspend fun stopProcessing(attemptId: String) = dao.stopProcessing(attemptId)
}
