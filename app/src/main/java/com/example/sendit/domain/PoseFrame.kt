package com.example.sendit.domain

/**
 * A pose observation at [timestamp] milliseconds from the start of a video.
 * Coordinates contain MediaPipe's 33 image landmarks in x/y/z/visibility/presence
 * order. An empty array means no pose and NaN confidence means unavailable.
 */
data class PoseFrame(
    val id: String,
    val attemptId: String,
    val frameIndex: Int,
    val timestamp: Long,
    val coordinates: FloatArray
) {
    companion object {
        const val LANDMARK_COUNT = 33
        const val VALUES_PER_LANDMARK = 5
    }
}
