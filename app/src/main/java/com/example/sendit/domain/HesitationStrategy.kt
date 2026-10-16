package com.example.sendit.domain

import java.util.UUID
import kotlin.math.hypot

class HesitationStrategy : ClassificationStrategy {
    // Looks for exploration without sustained pelvis displacement, rather than requiring still hips in every frame.
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

        var windowStart = 0
        var windowEnd = 0
        for (index in 1 until frames.size) {
            val previous = frames[index - 1]
            val current = frames[index]
            // Centre the observation window on the pair, shifting it inside the clip at either end.
            val centre = previous.timestamp + (current.timestamp - previous.timestamp) / 2
            val startTime = (centre - OBSERVATION_WINDOW_MS / 2).coerceIn(
                frames.first().timestamp,
                maxOf(frames.first().timestamp, frames.last().timestamp - OBSERVATION_WINDOW_MS)
            )
            while (windowStart + 1 < frames.size && frames[windowStart + 1].timestamp <= startTime) windowStart++
            while (windowEnd + 1 < frames.size && frames[windowEnd + 1].timestamp <= startTime + OBSERVATION_WINDOW_MS) windowEnd++
            val continuous = current.attemptId == previous.attemptId &&
                current.frameIndex == previous.frameIndex + 1 && current.timestamp > previous.timestamp
            val pairInWindow = index - 1 >= windowStart && index <= windowEnd
            if (continuous && pairInWindow && isExploring(frames, measurements, windowStart, windowEnd, scale)) {
                if (start < 0) start = index - 1
            } else {
                finish(index - 1)
            }
        }
        finish(frames.lastIndex)
        return findings
    }

    private fun isExploring(
        frames: List<PoseFrame>, measurements: List<Measurements?>, start: Int, end: Int, scale: Float
    ): Boolean {
        val first = measurements[start] ?: return false
        val last = measurements[end] ?: return false
        val seconds = (frames[end].timestamp - frames[start].timestamp) / MILLIS_PER_SECOND
        if (seconds <= 0f) return false
        val pelvisSpeed = distance(first.pelvis, last.pelvis) / scale / seconds
        if (pelvisSpeed >= MAX_PELVIS_SPEED) return false

        val anchors = arrayOfNulls<Point>(LIMB_LANDMARKS.size)
        var largestExcursion = 0f
        for (index in start + 1..end) {
            val previous = measurements[index - 1] ?: return false
            val current = measurements[index] ?: return false
            if (frames[index].attemptId != frames[index - 1].attemptId ||
                frames[index].frameIndex != frames[index - 1].frameIndex + 1 ||
                frames[index].timestamp <= frames[index - 1].timestamp
            ) return false
            for (limb in LIMB_LANDMARKS.indices) {
                val before = previous.limbs[limb]
                val after = current.limbs[limb]
                if (before != null && after != null) {
                    val anchor = anchors[limb] ?: before.also { anchors[limb] = it }
                    largestExcursion = maxOf(largestExcursion, distance(anchor, after))
                } else {
                    anchors[limb] = null
                }
            }
        }
        // Bounded displacement avoids accumulating tiny pose jitters into apparent exploration.
        return largestExcursion / scale >= MIN_LIMB_EXCURSION
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
        private const val MAX_PELVIS_SPEED = 0.3f // Net torso lengths per second across the window.
        private const val MIN_LIMB_EXCURSION = 0.1f // Torso lengths from an observed position in the window.
        private const val OBSERVATION_WINDOW_MS = 1_000L
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
