package com.example.sendit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import com.example.sendit.domain.PoseFrame

@Dao
interface AttemptDao {
    // Keeps any saved result when WorkManager restarts the same job.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attempt: AttemptEntity)

    // Saves the route entered on the upload form.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoute(route: RouteEntity)

    // Lists saved routes for the upload form's route picker.
    @Query("SELECT * FROM routes ORDER BY name")
    fun observeRoutes(): Flow<List<RouteEntity>>

    // Watches the saved attempt and frames for the detail page.
    @Transaction
    @Query("SELECT * FROM attempts WHERE id = :id")
    fun observeAttempt(id: String): Flow<AttemptWithFrames?>

    // Checks whether a restarted job already saved its result.
    @Query("SELECT * FROM attempts WHERE id = :id")
    suspend fun getById(id: String): AttemptEntity?

    // Saves every timestamped pose, including frames with no detected body.
    @Insert
    suspend fun insertFrames(frames: List<PoseFrame>)

    // Replaces only this attempt's frames when processing is repeated.
    @Query("DELETE FROM pose_frames WHERE attemptId = :attemptId")
    suspend fun deleteFrames(attemptId: String)

    // Removes an unfinished attempt; its pose frames go with it.
    @Query("DELETE FROM attempts WHERE id = :id")
    suspend fun deleteAttempt(id: String)

    // Removes a route only when none of its attempts remain.
    @Query("DELETE FROM routes WHERE id = :routeId AND NOT EXISTS (SELECT 1 FROM attempts WHERE routeId = :routeId)")
    suspend fun deleteRouteIfUnused(routeId: String)

    // Marks the result ready in the same transaction as its pose frames.
    @Query("UPDATE attempts SET processingStatus = 0, videoAspectRatio = :aspectRatio WHERE id = :id")
    suspend fun complete(id: String, aspectRatio: Float)

    // Marks a failed job as no longer processing; WorkManager records the failure.
    @Query("UPDATE attempts SET processingStatus = 0 WHERE id = :id")
    suspend fun stopProcessing(id: String)
}
