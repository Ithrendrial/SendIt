package com.example.sendit.domain

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.sendit.data.ClimbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Handles the analysis of the climbing attempt. 
// Worker gives an attempt Id, route id, and video URI. 
//ClimbAnalyser reads the video, extracts the pose frames, and saves them to the database.
class ClimbAnalyser(
    private val context: Context,
    private val extractor: PoseExtractor,
    private val repository: ClimbRepository
) {
    // Extracts and saves the reconstruction. Other analysis features will be added later.
    suspend fun processAttempt(attemptId: String, routeId: String, video: Uri) {
        val attempt = requireNotNull(repository.getAttempt(attemptId))
        require(attempt.routeId == routeId)
        val aspectRatio = readAspectRatio(video)
        val frames = extractor.extract(PoseExtractor.VideoInput(video.toString(), attemptId))
        repository.saveAnalysis(attemptId, frames, aspectRatio)
    }

    // Reads the video proportions, including portrait videos stored with a rotation tag.
    private suspend fun readAspectRatio(video: Uri): Float = withContext(Dispatchers.IO) {
        MediaMetadataRetriever().use { reader ->
            reader.setDataSource(context, video)
            val width = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
            val height = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
            val rotation = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            require(width != null && height != null && width > 0 && height > 0) { "Cannot read video dimensions" }
            if (rotation == 90 || rotation == 270) height / width else width / height
        }
    }
}
