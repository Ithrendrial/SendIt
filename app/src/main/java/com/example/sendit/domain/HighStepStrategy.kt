package com.example.sendit.domain

import java.util.UUID
import kotlin.math.abs

// Possible high-foot placements and step-ups; pose data cannot establish loading on a hold.
class HighStepStrategy : ClassificationStrategy {
    override fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding> {
        val measurements = PoseMeasurements(frames, aspectRatio)
        val scale = measurements.scale ?: return emptyList()
        val findings = mutableListOf<Finding>()
        for (leg in 0..1) {
            val raw = frames.indices.map { observe(measurements, it, leg, scale) }
            val poses = smooth(frames, raw, measurements)
            var historyStart = 0
            var windowStart = 0
            var cue = -1
            var eventStart = -1
            var lastObserved = -1
            var stableMs = 0L
            var setup: Observation? = null
            var timingAngle = 0f
            var latched = false

            fun resetCandidate() {
                cue = -1
                eventStart = -1
                stableMs = 0L
                setup = null
            }

            for (index in frames.indices) {
                if (index > 0 && !measurements.isFrameSequenceContinuous(index - 1, index)) {
                    resetCandidate()
                    historyStart = index
                    lastObserved = -1
                    latched = false
                }
                val current = poses[index]
                if (current == null) {
                    stableMs = 0L
                    setup = null
                    if (lastObserved < 0 || frames[index].timestamp - frames[lastObserved].timestamp > MAX_PENDING_GAP_MS) {
                        resetCandidate()
                        historyStart = index + 1
                    }
                    continue
                }
                if (lastObserved >= 0 && frames[index].timestamp - frames[lastObserved].timestamp > MAX_PENDING_GAP_MS) {
                    resetCandidate()
                    historyStart = index
                }
                val adjacent = lastObserved == index - 1
                lastObserved = index
                if (latched) {
                    if (current.height < -REARM_ANKLE_BELOW_HIP) latched = false else continue
                }
                if (cue >= 0 && frames[index].timestamp - frames[cue].timestamp > MAX_CONFIRMATION_MS) resetCandidate()
                if (cue < 0) {
                    while (historyStart < index && frames[index].timestamp - frames[historyStart].timestamp > LIFT_LOOKBACK_MS) historyStart++
                    val baseline = (historyStart until index).mapNotNull { poses[it] }.minByOrNull { it.height }
                        ?: continue
                    val higherThanSupport = current.otherFootDrop == null || current.otherFootDrop >= MIN_RELATIVE_FOOT_HEIGHT
                    if (!current.visible || !higherThanSupport || current.height < -MAX_ANKLE_BELOW_HIP ||
                        current.kneeAngle > MAX_SETUP_KNEE_ANGLE || current.height - baseline.height < MIN_ANKLE_LIFT ||
                        (baseline.ankle.y - current.ankle.y) / scale < MIN_ANKLE_LIFT) continue
                    cue = index
                    eventStart = index
                    windowStart = index
                }

                val boundary = frames[index].timestamp - OBSERVATION_WINDOW_MS
                while (windowStart + 1 <= index && frames[windowStart + 1].timestamp <= boundary) windowStart++
                val window = (windowStart..index).mapNotNull { poses[it] }
                val coherentFoot = window.all {
                    measurements.distance(window.first().ankle, it.ankle) / scale <= MAX_ANKLE_EXCURSION
                }
                if (!coherentFoot) stableMs = 0L
                else if (index > cue && adjacent) stableMs += frames[index].timestamp - frames[index - 1].timestamp
                if (setup == null && current.height < -MAX_ANKLE_BELOW_HIP) {
                    resetCandidate()
                    historyStart = index
                    continue
                }
                if (stableMs < MIN_SETTLING_MS) continue
                if (setup == null) {
                    val entry = (cue..index)
                        .filter { frames[it].timestamp - frames[cue].timestamp <= MIN_SETTLING_MS }
                    // A single visible frame followed by occluded estimates is insufficient.
                    if (current.kneeAngle > MAX_SETUP_KNEE_ANGLE || entry.count { poses[it]?.visible == true } * 2 < entry.size) {
                        resetCandidate()
                        historyStart = index
                        continue
                    }
                    setup = current
                    timingAngle = current.kneeAngle
                }
                val initial = requireNotNull(setup)
                if (measurements.distance(initial.ankle, current.ankle) / scale > MAX_PLACEMENT_RELOCATION) {
                    if (current.height >= -MAX_ANKLE_BELOW_HIP && current.kneeAngle <= MAX_SETUP_KNEE_ANGLE) {
                        setup = null // The foot is still being placed; require a new settling window.
                        stableMs = 0L
                    } else {
                        resetCandidate()
                        historyStart = index
                    }
                    continue
                }
                // A renewed bend can start a step after an earlier heel placement on the same high foot.
                if (current.kneeAngle < timingAngle - MIN_REPOSITION_FLEXION_DEGREES) {
                    timingAngle = current.kneeAngle
                    if (frames[index].timestamp - frames[cue].timestamp > LIFT_LOOKBACK_MS) eventStart = index
                }
                val compactPlacement = initial.height >= 0f && current.kneeAngle <= MAX_COMPACT_KNEE_ANGLE &&
                    current.lateralDistance <= MAX_COMPACT_LATERAL_DISTANCE && current.height >= -MAX_COMPACT_ANKLE_BELOW_HIP
                val progressed = current.visible && (initial.pelvis.y - current.pelvis.y) / scale >= MIN_BODY_RISE &&
                    current.kneeAngle - initial.kneeAngle >= MIN_KNEE_EXTENSION_DEGREES
                if (compactPlacement || progressed) {
                    findings += Finding(
                        UUID.randomUUID().toString(), frames[eventStart].attemptId, "HIGH_STEP",
                        frames[eventStart].frameIndex, frames[index].frameIndex
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
        val hip = measurements.landmarkAt(index, LEFT_HIP + leg, MIN_LEG_VISIBILITY, MIN_LEG_PRESENCE) ?: return null
        val knee = measurements.landmarkAt(index, LEFT_KNEE + leg, MIN_LEG_VISIBILITY, MIN_LEG_PRESENCE) ?: return null
        val ankle = measurements.landmarkAt(index, LEFT_ANKLE + leg, MIN_LEG_VISIBILITY, MIN_LEG_PRESENCE) ?: return null
        val angle = measurements.jointAngle(hip, knee, ankle).toFloat()
        if (!angle.isFinite()) return null
        val visible = listOf(LEFT_HIP + leg, LEFT_KNEE + leg, LEFT_ANKLE + leg)
            .all { measurements.landmarkAt(index, it) != null }
        val otherFootDrop = measurements.landmarkAt(index, LEFT_ANKLE + 1 - leg)?.let { (it.y - ankle.y) / scale }
        return Observation(pelvis, ankle, (hip.y - ankle.y) / scale, angle, visible, otherFootDrop, abs(hip.x - ankle.x) / scale)
    }

    private fun smooth(frames: List<PoseFrame>, raw: List<Observation?>, measurements: PoseMeasurements): List<Observation?> {
        var start = 0
        return frames.indices.map { index ->
            if (index > 0 && !measurements.isFrameSequenceContinuous(index - 1, index)) start = index
            while (start < index && frames[index].timestamp - frames[start].timestamp > OBSERVATION_WINDOW_MS) start++
            raw[index]?.let { current ->
                val window = (start..index).mapNotNull { raw[it] }
                current.copy(
                    ankle = PoseMeasurements.Point(median(window.map { it.ankle.x }), median(window.map { it.ankle.y })),
                    height = median(window.map { it.height }),
                    kneeAngle = median(window.map { it.kneeAngle })
                )
            }
        }
    }

    private fun median(values: List<Float>) = values.sorted().let { it[it.size / 2] }

    private data class Observation(
        val pelvis: PoseMeasurements.Point, val ankle: PoseMeasurements.Point, val height: Float,
        val kneeAngle: Float, val visible: Boolean, val otherFootDrop: Float?, val lateralDistance: Float
    )

    private companion object {
        // Engineering tolerances calibrated on annotated clips; source basis and limitations live outside Git.
        const val OBSERVATION_WINDOW_MS = 200L
        const val LIFT_LOOKBACK_MS = 1_500L
        const val MAX_CONFIRMATION_MS = 5_000L
        const val MAX_PENDING_GAP_MS = 150L
        const val MIN_SETTLING_MS = 300L
        const val MIN_ANKLE_LIFT = 0.35f // Distances below are torso lengths.
        const val MAX_ANKLE_BELOW_HIP = 1f
        const val MIN_RELATIVE_FOOT_HEIGHT = 0.25f
        const val MAX_ANKLE_EXCURSION = 0.3f
        const val MAX_PLACEMENT_RELOCATION = 1f
        const val MIN_BODY_RISE = 0.5f
        const val REARM_ANKLE_BELOW_HIP = 0.75f
        const val MAX_SETUP_KNEE_ANGLE = 130f
        const val MAX_COMPACT_KNEE_ANGLE = 100f
        const val MAX_COMPACT_LATERAL_DISTANCE = 1f
        const val MAX_COMPACT_ANKLE_BELOW_HIP = 0.5f
        const val MIN_KNEE_EXTENSION_DEGREES = 20f
        const val MIN_REPOSITION_FLEXION_DEGREES = 10f
        const val MIN_LEG_VISIBILITY = 0f
        const val MIN_LEG_PRESENCE = 0.9f
        const val LEFT_HIP = 23
        const val LEFT_KNEE = 25
        const val LEFT_ANKLE = 27
    }
}
