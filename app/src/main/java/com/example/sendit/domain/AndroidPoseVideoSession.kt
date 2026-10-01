package com.example.sendit.domain

import android.content.Context
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/** Reads one video and performs pose detection on each frame. Frame released from memoryafter processing.*/
internal class AndroidPoseVideoSession private constructor(
    private val reader: VideoFrameReader,
    private val landmarker: PoseLandmarker
) : PoseExtractor.VideoSession {
    override val totalFrames: Int get() = reader.totalFrames

    override fun next(): PoseLandmarkerResult? {
        val frame = reader.next() ?: return null
        frame.use {
            BitmapImageBuilder(frame.bitmap).build().use { image ->
                return landmarker.detectForVideo(image, frame.timestampMs)
            }
        }
    }

    override fun close() {
        try {
            landmarker.close()
        } finally {
            reader.close()
        }
    }

    // Companion objects allow access to class functions without creating an instance of the class.
    // This function opens a video file and initializes the pose landmarker.
    companion object {
        suspend fun open(context: Context, uri: Uri): AndroidPoseVideoSession {
            val reader = VideoFrameReader.open(context, uri)
            try {
                val landmarker = PoseLandmarker.createFromOptions(context, PoseExtractor.createOptions())
                return AndroidPoseVideoSession(reader, landmarker)
            } catch (failure: Throwable) {
                try {
                    reader.close()
                } catch (cleanupFailure: Exception) {
                    failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
        }
    }
}
