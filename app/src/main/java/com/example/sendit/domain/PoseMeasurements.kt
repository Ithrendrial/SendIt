package com.example.sendit.domain

import kotlin.math.hypot

// Shared image-plane measurements for classification strategies; no persistence or Android work.
internal class PoseMeasurements(private val frames: List<PoseFrame>, private val aspectRatio: Float) {
    init {
        require(aspectRatio.isFinite() && aspectRatio > 0f) { "Aspect ratio must be positive and finite" }
    }

    private val measurements = frames.map { measure(it, aspectRatio) }
    val scale: Float? = measurements.mapNotNull { it?.torsoLength }.sorted().let { lengths ->
        if (lengths.isEmpty()) null else (lengths[(lengths.size - 1) / 2] + lengths[lengths.size / 2]) / 2f
    }

    fun isUsable(index: Int) = measurements[index] != null

    fun landmarkAt(framePosition: Int, landmarkId: Int): Point? {
        val frame = frames[framePosition]
        if (frame.coordinates.size != PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) return null
        return landmark(frame, landmarkId, aspectRatio)
    }

    fun isContinuous(previous: Int, current: Int) = isUsable(previous) && isUsable(current) &&
        frames[current].attemptId == frames[previous].attemptId &&
        frames[current].frameIndex == frames[previous].frameIndex + 1 &&
        frames[current].timestamp > frames[previous].timestamp

    data class Window(val pelvisSpeed: Float, val limbExcursion: Float)

    fun window(start: Int, end: Int): Window? {
        val scale = scale ?: return null
        val first = measurements[start] ?: return null
        val last = measurements[end] ?: return null
        val seconds = (frames[end].timestamp - frames[start].timestamp) / MILLIS_PER_SECOND
        if (seconds <= 0f) return null
        val pelvisSpeed = distance(first.pelvis, last.pelvis) / scale / seconds

        val anchors = arrayOfNulls<Point>(LIMB_LANDMARKS.size)
        var largestExcursion = 0f
        for (index in start + 1..end) {
            val previous = measurements[index - 1] ?: return null
            val current = measurements[index] ?: return null
            if (!isContinuous(index - 1, index)) return null
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
        return Window(pelvisSpeed, largestExcursion / scale)
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

    fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    // Internal calculation values, not stored entities or new architectural components.
    data class Point(val x: Float, val y: Float)
    private data class Measurements(val pelvis: Point, val torsoLength: Float, val limbs: List<Point?>)

    companion object {
        // Provisional engineering estimates.
        const val MIN_LIMB_EXCURSION = 0.1f // Torso lengths from an observed position in the window.
        const val OBSERVATION_WINDOW_MS = 1_000L
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
