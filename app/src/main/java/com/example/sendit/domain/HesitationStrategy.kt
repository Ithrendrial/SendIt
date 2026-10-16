package com.example.sendit.domain

import java.util.UUID
import kotlin.math.hypot

class HesitationStrategy : ClassificationStrategy {
    // Finds likely exploration without attempt progression. Just a motionless pause alone is not enough to 
    // classify as hesitation.
    override fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding> {
        require(aspectRatio.isFinite() && aspectRatio > 0f) { "Aspect ratio must be positive and finite" }
        if (frames.size < 2) return emptyList()

        val measurements = frames.map { measure(it, aspectRatio) }
        val torsoLengths = measurements.mapNotNull { it?.torsoLength }.sorted()
        if (torsoLengths.isEmpty()) return emptyList()
        val scale = (torsoLengths[(torsoLengths.size - 1) / 2] + torsoLengths[torsoLengths.size / 2]) / 2f

        val findings = mutableListOf<Finding>()
        var start = -1

        // Close each continuous run before an invalid/moving pair, or at the end of the clip.
        fun finish(end: Int) {
            if (start >= 0 && frames[end].timestamp - frames[start].timestamp >= MIN_HESITATION_MS) {
                findings += Finding(
                    id = UUID.randomUUID().toString(),
                    attemptId = frames[start].attemptId,
                    type = "HESITATION",
                    startFrameIndex = frames[start].frameIndex,
                    endFrameIndex = frames[end].frameIndex
                )
            }
            start = -1
        }

        for (index in 1 until frames.size) {
            val previous = frames[index - 1]
            val current = frames[index]
            val seconds = (current.timestamp - previous.timestamp) / MILLIS_PER_SECOND
            val continuous = current.attemptId == previous.attemptId &&
                current.frameIndex == previous.frameIndex + 1
            if (continuous && isExploring(measurements[index - 1], measurements[index], seconds, scale)) {
                if (start < 0) start = index - 1
            } else {
                finish(index - 1)
            }
        }
        finish(frames.lastIndex)
        return findings
    }

    private fun isExploring(previous: Measurements?, current: Measurements?, seconds: Float, scale: Float): Boolean {
        if (previous == null || current == null || seconds <= 0f) return false
        val pelvisSpeed = distance(previous.pelvis, current.pelvis) / scale / seconds
        if (pelvisSpeed >= MAX_PELVIS_SPEED) return false
        return LIMB_LANDMARKS.indices.any { index ->
            val before = previous.limbs[index]
            val after = current.limbs[index]
            before != null && after != null && distance(before, after) / scale / seconds >= MIN_LIMB_SPEED
        }
    }

    // Measurements share an image-plane scale: normalized x is multiplied by width/height.
    private fun measure(frame: PoseFrame, aspectRatio: Float): Measurements? {
        if (frame.coordinates.size != PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) return null
        val leftHip = landmark(frame, LEFT_HIP, aspectRatio) ?: return null
        val rightHip = landmark(frame, RIGHT_HIP, aspectRatio) ?: return null
        val leftShoulder = landmark(frame, LEFT_SHOULDER, aspectRatio) ?: return null
        val rightShoulder = landmark(frame, RIGHT_SHOULDER, aspectRatio) ?: return null
        val pelvis = midpoint(leftHip, rightHip)
        val torsoLength = distance(pelvis, midpoint(leftShoulder, rightShoulder))
        if (!torsoLength.isFinite() || torsoLength <= 0f) return null
        return Measurements(pelvis, torsoLength, LIMB_LANDMARKS.map { landmark(frame, it, aspectRatio) })
    }

    private fun landmark(frame: PoseFrame, index: Int, aspectRatio: Float): Point? {
        val offset = index * PoseFrame.VALUES_PER_LANDMARK
        val coordinates = frame.coordinates
        val visibility = coordinates[offset + VISIBILITY_OFFSET]
        val presence = coordinates[offset + PRESENCE_OFFSET]
        // Comparisons also reject NaN: unknown confidence must not become trusted evidence.
        if (!(visibility >= MIN_LANDMARK_CONFIDENCE && presence >= MIN_LANDMARK_CONFIDENCE)) return null
        val x = coordinates[offset] * aspectRatio
        val y = coordinates[offset + 1]
        return if (x.isFinite() && y.isFinite()) Point(x, y) else null
    }

    private fun midpoint(a: Point, b: Point) = Point((a.x + b.x) / 2f, (a.y + b.y) / 2f)

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    // Internal calculation values, not stored entities or new architectural components.
    private data class Point(val x: Float, val y: Float)
    private data class Measurements(val pelvis: Point, val torsoLength: Float, val limbs: List<Point?>)

    private companion object {
        // Provisional engineering estimates.
        private const val MAX_PELVIS_SPEED = 0.1f // Torso lengths per second.
        private const val MIN_LIMB_SPEED = 0.2f // Torso lengths per second.
        private const val MIN_HESITATION_MS = 1_000L
        private const val MIN_LANDMARK_CONFIDENCE = 0.5f
        private const val MILLIS_PER_SECOND = 1_000f

        // MediaPipe landmark IDs and offsets in PoseFrame's existing coordinate layout.
        private const val LEFT_SHOULDER = 11
        private const val RIGHT_SHOULDER = 12
        private const val LEFT_HIP = 23
        private const val RIGHT_HIP = 24
        private val LIMB_LANDMARKS = listOf(15, 16, 27, 28) // Wrists, then ankles.
        private const val VISIBILITY_OFFSET = 3
        private const val PRESENCE_OFFSET = 4
    }
}
