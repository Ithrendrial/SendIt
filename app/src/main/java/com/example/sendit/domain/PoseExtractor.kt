package com.example.sendit.domain

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Converts a video's sequential detections into the UML's timestamped pose records. */
class PoseExtractor internal constructor(
    private val openVideo: suspend (String) -> VideoSession
) {
    // Sets up the extractor to read videos on the phone.
    constructor(context: Context) : this(openVideo = androidVideoOpener(context))

    data class VideoInput(val uri: String, val attemptId: String)

    internal interface VideoSession : Closeable {
        // Returns the next pose result, or null when the video has finished.
        fun next(): PoseLandmarkerResult?
    }

    // Extracts the video's poses in the background and links each frame to the attempt.
    suspend fun extract(video: VideoInput): List<PoseFrame> = withContext(Dispatchers.Default) {
        require(video.uri.isNotBlank()) { "A video URI is required" }
        require(video.attemptId.isNotBlank()) { "An attempt ID is required" }
        currentCoroutineContext().ensureActive()
        openVideo(video.uri).use { session ->
            val frames = mutableListOf<PoseFrame>()
            var previousTimestamp = -1L
            while (true) {
                // Stops between frames if the processing job has been cancelled.
                currentCoroutineContext().ensureActive()
                val result = session.next() ?: break
                currentCoroutineContext().ensureActive()
                val timestamp = result.timestampMs()
                require(timestamp >= 0 && timestamp > previousTimestamp) {
                    "Video timestamps must be non-negative and strictly increasing"
                }
                frames += PoseFrame(
                    id = UUID.randomUUID().toString(),
                    attemptId = video.attemptId,
                    frameIndex = frames.size,
                    timestamp = timestamp,
                    coordinates = readCoordinates(result)
                )
                previousTimestamp = timestamp
            }
            frames
        }
    }

    // Puts the landmarks and confidence values into the coordinate array. No pose gives an empty array.
    private fun readCoordinates(result: PoseLandmarkerResult): FloatArray {
        val poses = result.landmarks()
        require(poses.size <= CLIMBERS_PER_FRAME) { "Expected at most one pose per frame" }
        val landmarks = poses.firstOrNull().orEmpty()
        require(landmarks.isEmpty() || landmarks.size == PoseFrame.LANDMARK_COUNT) {
            "A detected pose must contain ${PoseFrame.LANDMARK_COUNT} landmarks"
        }

        // Keep the agreed x/y/z/visibility/presence order without index arithmetic.
        val coordinates = ArrayList<Float>(landmarks.size * PoseFrame.VALUES_PER_LANDMARK)
        for (landmark in landmarks) {
            coordinates.add(landmark.x())
            coordinates.add(landmark.y())
            coordinates.add(landmark.z())
            // NaN means the model did not provide this confidence value.
            coordinates.add(landmark.visibility().orElse(Float.NaN))
            coordinates.add(landmark.presence().orElse(Float.NaN))
        }
        return coordinates.toFloatArray()
    }

    companion object {
        private const val MODEL_ASSET_PATH = "pose_landmarker_full.task"
        private const val CLIMBERS_PER_FRAME = 1

        // Creates the function that opens a real video session when extraction starts.
        private fun androidVideoOpener(context: Context): suspend (String) -> VideoSession {
            // The application context avoids keeping an old screen in memory after rotation.
            val applicationContext = context.applicationContext
            return { uri -> AndroidPoseVideoSession.open(applicationContext, Uri.parse(uri)) }
        }

        // Sets up MediaPipe to use the saved model and look for one climber in a video.
        internal fun createOptions(): PoseLandmarker.PoseLandmarkerOptions {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .setDelegate(Delegate.CPU)
                .build()

            return PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(CLIMBERS_PER_FRAME)
                .build()
        }
    }
}
