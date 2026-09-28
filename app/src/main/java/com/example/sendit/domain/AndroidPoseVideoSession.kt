package com.example.sendit.domain

import android.content.Context
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/** One local video's reader and MediaPipe engine; never retains decoded images. */
internal class AndroidPoseVideoSession private constructor(
    private val reader: VideoFrameReader,
    private val landmarker: PoseLandmarker
) : PoseExtractor.VideoSession {
    // Reads the next video frame and passes it to MediaPipe for pose detection.
    override fun next(): PoseLandmarkerResult? {
        val frame = reader.next() ?: return null
        // use releases both images after detection, even if an error occurs.
        frame.use {
            BitmapImageBuilder(frame.bitmap).build().use { image ->
                return landmarker.detectForVideo(image, frame.timestampMs)
            }
        }
    }

    // Releases MediaPipe and the video reader when the session finishes.
    override fun close() {
        try {
            landmarker.close()
        } finally {
            reader.close()
        }
    }

    companion object {
        // Opens the video and starts a fresh MediaPipe engine for this session.
        suspend fun open(context: Context, uri: Uri): AndroidPoseVideoSession {
            val reader = VideoFrameReader.open(context, uri)
            try {
                val landmarker = PoseLandmarker.createFromOptions(context, PoseExtractor.createOptions())
                return AndroidPoseVideoSession(reader, landmarker)
            } catch (failure: Throwable) {
                try {
                    reader.close()
                } catch (cleanupFailure: Exception) {
                    // Keep the original error and attach any error from closing the reader.
                    failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
        }
    }
}
