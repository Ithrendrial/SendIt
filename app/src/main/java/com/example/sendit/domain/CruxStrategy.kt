package com.example.sendit.domain

import java.util.UUID

// Returns one estimated crux: longest hesitation, slowest moving window, or earliest usable interval.
class CruxStrategy : ClassificationStrategy {
    override fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding> {
        val measurements = PoseMeasurements(frames, aspectRatio)
        val firstUsable = requireNotNull(frames.indices.firstOrNull { measurements.isUsable(it) }) {
            "Cannot estimate a crux without usable pose data"
        }

        val timestamps = frames.associate { it.frameIndex to it.timestamp }
        val hesitation = HesitationStrategy().classify(frames, aspectRatio).maxByOrNull {
            timestamps.getValue(it.endFrameIndex) - timestamps.getValue(it.startFrameIndex)
        }
        if (hesitation != null) {
            return listOf(hesitation.copy(id = UUID.randomUUID().toString(), type = "CRUX"))
        }

        // The fallback has no difficulty claim: use the first continuous usable span, up to a second.
        var fallbackEnd = firstUsable
        while (fallbackEnd + 1 < frames.size && measurements.isContinuous(fallbackEnd, fallbackEnd + 1) &&
            frames[fallbackEnd + 1].timestamp - frames[firstUsable].timestamp <= PoseMeasurements.OBSERVATION_WINDOW_MS
        ) fallbackEnd++

        var bestStart = firstUsable
        var bestEnd = fallbackEnd
        var slowestSpeed = Float.POSITIVE_INFINITY
        var end = firstUsable
        for (start in firstUsable until frames.size) {
            if (!measurements.isUsable(start)) continue
            end = maxOf(end, start)
            val targetTime = frames[start].timestamp + PoseMeasurements.OBSERVATION_WINDOW_MS
            while (end + 1 < frames.size && frames[end].timestamp < targetTime) end++
            if (frames[end].timestamp < targetTime) break
            val window = measurements.window(start, end) ?: continue
            // Exclude fully still rests from the moving-window ranking. Equal ranks keep the earlier window.
            if (window.limbExcursion >= PoseMeasurements.MIN_LIMB_EXCURSION && window.pelvisSpeed < slowestSpeed) {
                slowestSpeed = window.pelvisSpeed
                bestStart = start
                bestEnd = end
            }
        }
        return listOf(Finding(
            id = UUID.randomUUID().toString(),
            attemptId = frames[bestStart].attemptId,
            type = "CRUX",
            startFrameIndex = frames[bestStart].frameIndex,
            endFrameIndex = frames[bestEnd].frameIndex
        ))
    }
}
