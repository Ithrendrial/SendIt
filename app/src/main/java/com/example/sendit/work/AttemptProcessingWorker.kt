package com.example.sendit.work

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.sendit.SendItApplication
import com.example.sendit.data.AttemptEntity
import com.example.sendit.data.RouteEntity
import com.example.sendit.domain.AttemptDetails
import com.example.sendit.domain.ClimbAnalyser
import com.example.sendit.domain.PoseExtractor
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Reads the selected video URI and form details from selected job. job Id becomes the attempt Id. 
// It then shows a processing notification and then saves the route and attempt details to the database.
// The video is copied to temp storage and ClimbAnalyser extracts and saves the poses to the database.
// Finally, return success or failure to WorkManager.
class AttemptProcessingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    // Runs the UML processing sequence, keeping only the saved reconstruction afterwards.
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val video = Uri.parse(inputData.getString(VIDEO_URI) ?: return@withContext Result.failure())
        val repository = (applicationContext as SendItApplication).repository
        val attemptId = id.toString()
        val routeId = requireNotNull(inputData.getString(ROUTE_ID))
        val temporaryVideo = File(applicationContext.noBackupFilesDir, "processing/$attemptId.mp4")
        var finished = false
        try {
            setForeground(processingNotification())
            val route = RouteEntity(routeId, inputData.getString("routeName").orEmpty(),
                inputData.getString("grade").orEmpty(), inputData.getString("location").orEmpty())
            val attempt = AttemptEntity(attemptId, routeId, inputData.getLong("recordedAt", 0),
                inputData.getString("outcome").orEmpty(), inputData.getString("notes").orEmpty())
            repository.saveRoute(route, attempt)
            // WorkManager may restart after saving but before reporting success.
            if (repository.getAttempt(attemptId)?.videoAspectRatio == null) {
                copyVideo(video, temporaryVideo)
                ClimbAnalyser(applicationContext, PoseExtractor(applicationContext), repository)
                    .processAttempt(attemptId, routeId, Uri.fromFile(temporaryVideo))
            }
            finished = true
            Result.success()
        } catch (cancelled: CancellationException) {
            // Keep URI access so WorkManager can retry after a system interruption.
            throw cancelled
        } catch (failure: Exception) {
            Log.e("AttemptProcessing", "Could not process attempt $attemptId", failure)
            repository.stopProcessing(attemptId)
            finished = true
            Result.failure()
        } finally {
            temporaryVideo.delete()
            if (finished) releaseVideoAccess(applicationContext, video)
        }
    }

    // Copies the selected video into app-owned storage; never changes the user's source file.
    private fun copyVideo(video: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        val input = applicationContext.contentResolver.openInputStream(video)
            ?: throw IOException("Cannot open selected video")
        input.use { source ->
            destination.outputStream().use { target -> source.copyTo(target) }
        }
    }

    // Android requires a foreground notification for longer video processing.
    private fun processingNotification(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(PROCESSING_CHANNEL, "Video processing", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, PROCESSING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Processing your climb")
            .setContentText("Extracting body positions from the selected video")
            .setOngoing(true)
            .build()
        // Android 15 added a service type specifically for processing media files.
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        return ForegroundInfo(PROCESSING_NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val WORK_NAME = "process_attempt"
        private const val PROCESSING_CHANNEL = "pose_processing"
        private const val PROCESSING_NOTIFICATION_ID = 1
        private const val VIDEO_URI = "videoUri"
        private const val ROUTE_ID = "routeId"

        // Releases access after processing or a failed submission; the grant may already be gone.
        fun releaseVideoAccess(context: Context, video: Uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(video, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        // Captures the form values so later edits cannot change an already queued attempt.
        fun request(video: Uri, details: AttemptDetails) = OneTimeWorkRequestBuilder<AttemptProcessingWorker>()
            .setInputData(workDataOf(
                VIDEO_URI to video.toString(), ROUTE_ID to UUID.randomUUID().toString(),
                "routeName" to details.routeName, "grade" to details.grade, "location" to details.location,
                "recordedAt" to details.recordedAt, "outcome" to details.outcome, "notes" to details.notes
            ))
            .build()
    }
}
