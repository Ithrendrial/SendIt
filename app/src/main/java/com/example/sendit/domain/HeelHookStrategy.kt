package com.example.sendit.domain

import java.util.UUID

// Possible heel placement/use events. The end marks confirmation, not verified contact release.
class HeelHookStrategy : ClassificationStrategy {
    override fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding> {
        val measurements = PoseMeasurements(frames, aspectRatio)
        val scale = measurements.scale ?: return emptyList()
        val findings = mutableListOf<Finding>()
        for (leg in 0..1) {
            val raw = frames.indices.map { observe(measurements, it, leg, scale) }
            val observations = smooth(frames, raw, measurements)
            var placement = -1
            var lastReliable = -1
            var windowStart = 0
            var historyStart = 0
            var stableMs = 0L
            var flexion = 0f
            var latched = false

            fun resetCandidate() {
                placement = -1
                lastReliable = -1
                stableMs = 0L
                flexion = 0f
            }

            for (index in frames.indices) {
                if (index > 0 && !measurements.isFrameSequenceContinuous(index - 1, index)) {
                    resetCandidate()
                    latched = false
                    historyStart = index
                }
                val current = observations[index]
                if (current == null) {
                    // Retain a brief pending cue, but never add unknown time to confirmed settling.
                    stableMs = 0L
                    if (placement >= 0 && (frames[index].timestamp - frames[lastReliable].timestamp > MAX_PENDING_GAP_MS ||
                            frames[index].timestamp - frames[placement].timestamp > MAX_CONFIRMATION_MS)) resetCandidate()
                    continue
                }
                if (latched) {
                    if (current.height < -REARM_HEEL_BELOW_PELVIS) latched = false else continue
                }
                if (placement >= 0 && (frames[index].timestamp - frames[placement].timestamp > MAX_CONFIRMATION_MS ||
                        index != lastReliable + 1 && frames[index].timestamp - frames[lastReliable].timestamp > MAX_PENDING_GAP_MS)) {
                    resetCandidate()
                }
                if (placement < 0) {
                    while (historyStart < index && frames[index].timestamp - frames[historyStart].timestamp > LIFT_LOOKBACK_MS) historyStart++
                    val baseline = (historyStart until index).mapNotNull { observations[it] }.minByOrNull { it.height }
                        ?: continue
                    if (current.height < -MAX_HEEL_BELOW_PELVIS || !current.toeUp ||
                        current.height - baseline.height < MIN_HEEL_LIFT ||
                        (baseline.heel.y - current.heel.y) / scale < MIN_HEEL_LIFT) continue
                    placement = index
                    lastReliable = index
                    windowStart = index
                }

                val boundary = frames[index].timestamp - OBSERVATION_WINDOW_MS
                while (windowStart + 1 <= index && frames[windowStart + 1].timestamp <= boundary) windowStart++
                val window = (windowStart..index).mapNotNull { observations[it] }
                val stable = window.all { measurements.distance(window.first().heel, it.heel) / scale <= MAX_HEEL_EXCURSION }
                if (!stable) {
                    stableMs = 0L
                } else if (lastReliable == index - 1) {
                    val elapsed = frames[index].timestamp - frames[lastReliable].timestamp
                    stableMs += elapsed
                }
                lastReliable = index

                if (frames[index].timestamp - frames[placement].timestamp >= OBSERVATION_WINDOW_MS) {
                    val initialAngles = (placement..index)
                        .filter { frames[it].timestamp - frames[placement].timestamp <= OBSERVATION_WINDOW_MS }
                        .mapNotNull { observations[it]?.kneeAngle }
                    val currentAngles = window.mapNotNull { it.kneeAngle }
                    if (initialAngles.isNotEmpty() && currentAngles.isNotEmpty()) {
                        flexion = maxOf(flexion, median(initialAngles) - median(currentAngles))
                    }
                }
                // Toe-up establishes placement; subsequent foot rotation is allowed during observed leg use.
                if (stableMs >= MIN_SETTLING_MS &&
                    current.height >= -MAX_HEEL_BELOW_PELVIS && flexion >= MIN_KNEE_FLEXION_DEGREES) {
                    findings += Finding(
                        id = UUID.randomUUID().toString(), attemptId = frames[placement].attemptId,
                        type = "HEEL_HOOK", startFrameIndex = frames[placement].frameIndex,
                        endFrameIndex = frames[index].frameIndex
                    )
                    latched = true
                    resetCandidate()
                }
            }
        }
        return findings.sortedBy { it.startFrameIndex }
    }

    private fun observe(measurements: PoseMeasurements, index: Int, leg: Int, scale: Float): Observation? {
        val pelvis = measurements.pelvisAt(index) ?: return null
        val heel = measurements.landmarkAt(index, LEFT_HEEL + leg) ?: return null
        val toe = measurements.landmarkAt(index, LEFT_FOOT_INDEX + leg) ?: return null
        val hip = measurements.landmarkAt(index, LEFT_HIP + leg)
        val knee = measurements.landmarkAt(index, LEFT_KNEE + leg)
        val ankle = measurements.landmarkAt(index, LEFT_ANKLE + leg)
        val angle = if (hip != null && knee != null && ankle != null) {
            measurements.jointAngle(hip, knee, ankle).takeIf { it.isFinite() }?.toFloat()
        } else null
        return Observation(heel, (pelvis.y - heel.y) / scale, (heel.y - toe.y) / scale >= MIN_TOE_RISE, angle)
    }

    private fun smooth(frames: List<PoseFrame>, raw: List<Observation?>, measurements: PoseMeasurements): List<Observation?> {
        var start = 0
        return frames.indices.map { index ->
            if (index > 0 && !measurements.isFrameSequenceContinuous(index - 1, index)) start = index
            while (start < index && frames[index].timestamp - frames[start].timestamp > OBSERVATION_WINDOW_MS) start++
            raw[index]?.let { current ->
                val window = (start..index).mapNotNull { raw[it] } // Contains current; missing samples stay missing.
                current.copy(
                    heel = PoseMeasurements.Point(median(window.map { it.heel.x }), median(window.map { it.heel.y })),
                    height = median(window.map { it.height })
                )
            }
        }
    }

    private fun median(values: List<Float>): Float = values.sorted().let { it[it.size / 2] }

    private data class Observation(
        val heel: PoseMeasurements.Point, val height: Float, val toeUp: Boolean, val kneeAngle: Float?
    )

    private companion object {
        // Uncalibrated engineering estimates; source/rationale and trial results are in workspace docs.
        const val OBSERVATION_WINDOW_MS = 200L
        const val LIFT_LOOKBACK_MS = 1_500L
        const val MAX_CONFIRMATION_MS = 2_500L
        const val MAX_PENDING_GAP_MS = 500L
        const val MIN_SETTLING_MS = 500L
        const val MIN_HEEL_LIFT = 0.35f // Torso lengths: image rise and rise relative to pelvis.
        const val MAX_HEEL_BELOW_PELVIS = 0.5f // Torso lengths at placement and confirmation.
        const val REARM_HEEL_BELOW_PELVIS = 0.75f // Separate threshold avoids duplicate events while raised.
        const val MIN_TOE_RISE = 0.05f // Torso lengths above heel.
        const val MAX_HEEL_EXCURSION = 0.25f // Torso lengths across each filtered observation window.
        const val MIN_KNEE_FLEXION_DEGREES = 20f // Change during use, not a prescribed knee angle.
        const val LEFT_HIP = 23
        const val LEFT_KNEE = 25
        const val LEFT_ANKLE = 27
        const val LEFT_HEEL = 29
        const val LEFT_FOOT_INDEX = 31
    }
}
