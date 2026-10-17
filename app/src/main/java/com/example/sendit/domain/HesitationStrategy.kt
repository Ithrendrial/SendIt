package com.example.sendit.domain

import java.util.UUID

class HesitationStrategy : ClassificationStrategy {
    // Looks for exploration without sustained pelvis displacement, rather than requiring still hips in every frame.
    override fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding> {
        require(aspectRatio.isFinite() && aspectRatio > 0f) { "Aspect ratio must be positive and finite" }
        if (frames.size < 2) return emptyList()

        val measurements = PoseMeasurements(frames, aspectRatio)
        if (measurements.scale == null) return emptyList()

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
            val startTime = (centre - PoseMeasurements.OBSERVATION_WINDOW_MS / 2).coerceIn(
                frames.first().timestamp,
                maxOf(frames.first().timestamp, frames.last().timestamp - PoseMeasurements.OBSERVATION_WINDOW_MS)
            )
            while (windowStart + 1 < frames.size && frames[windowStart + 1].timestamp <= startTime) windowStart++
            while (windowEnd + 1 < frames.size && frames[windowEnd + 1].timestamp <= startTime + PoseMeasurements.OBSERVATION_WINDOW_MS) windowEnd++
            val continuous = measurements.isContinuous(index - 1, index)
            val pairInWindow = index - 1 >= windowStart && index <= windowEnd
            val window = if (continuous && pairInWindow) measurements.window(windowStart, windowEnd) else null
            if (window != null && window.pelvisSpeed < MAX_PELVIS_SPEED &&
                window.limbExcursion >= PoseMeasurements.MIN_LIMB_EXCURSION
            ) {
                if (start < 0) start = index - 1
            } else {
                finish(index - 1)
            }
        }
        finish(frames.lastIndex)
        return findings
    }

    private companion object {
        private const val MAX_PELVIS_SPEED = 0.3f // Net torso lengths per second across the window.
        private const val MIN_HESITATION_MS = 1_000L
    }
}
