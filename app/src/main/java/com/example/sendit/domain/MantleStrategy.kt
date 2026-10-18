package com.example.sendit.domain

import java.util.UUID
import kotlin.math.abs

// Possible mantle phases using a model-supported arm, body progression and a nearby raised foot.
class MantleStrategy : ClassificationStrategy {
    override fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding> {
        val measurements = PoseMeasurements(frames, aspectRatio)
        val scale = measurements.scale ?: return emptyList()
        val findings = mutableListOf<Finding>()
        for (arm in 0..1) {
            val wrists = smoothWrists(frames, measurements, arm)
            for (leg in 0..1) {
                val poses = frames.indices.map { observe(measurements, it, arm, leg, wrists[it]) }
                var historyStart = 0
                var windowStart = 0
                var setup = -1
                var confirmationStart = -1
                var lastObserved = -1
                var completedHand: PoseMeasurements.Point? = null

                fun resetConfirmation() {
                    setup = -1
                    confirmationStart = -1
                }

                for (index in frames.indices) {
                    if (index > 0 && !measurements.isFrameSequenceContinuous(index - 1, index)) {
                        historyStart = index
                        windowStart = index
                        lastObserved = -1
                        completedHand = null
                        resetConfirmation()
                    }
                    val current = poses[index]
                    if (current == null) {
                        resetConfirmation() // Unknown observations never add confirmation time.
                        if (lastObserved < 0 || frames[index].timestamp - frames[lastObserved].timestamp > MAX_PENDING_GAP_MS) {
                            historyStart = index + 1
                            windowStart = index + 1
                        }
                        continue
                    }
                    val completed = completedHand
                    if (completed != null) {
                        if (measurements.distance(completed, current.wrist) / scale <= REARM_WRIST_DISTANCE) continue
                        completedHand = null
                        historyStart = index
                        windowStart = index
                        lastObserved = -1
                        resetConfirmation()
                    }
                    if (lastObserved >= 0 && frames[index].timestamp - frames[lastObserved].timestamp > MAX_PENDING_GAP_MS) {
                        historyStart = index
                        windowStart = index
                        resetConfirmation()
                    }
                    while (historyStart < index && frames[index].timestamp - frames[historyStart].timestamp > MAX_SEQUENCE_MS) historyStart++
                    if (setup >= 0 && setup < historyStart) resetConfirmation()
                    if (setup < 0) {
                        // Retain recent setup observations instead of timing out on the first pull pose.
                        setup = (historyStart until index).firstOrNull { candidate ->
                            val initial = poses[candidate]
                            initial != null && progressed(
                                initial, current, frames[index].timestamp - frames[candidate].timestamp, scale
                            )
                        } ?: -1
                    }
                    if (setup < 0) {
                        lastObserved = index
                        continue
                    }

                    val boundary = frames[index].timestamp - OBSERVATION_WINDOW_MS
                    while (windowStart + 1 <= index && frames[windowStart + 1].timestamp <= boundary) windowStart++
                    val window = (windowStart..index).mapNotNull { poses[it] }
                    val coherentHand = window.all {
                        measurements.distance(window.first().wrist, it.wrist) / scale <= MAX_HAND_EXCURSION
                    }
                    val initial = requireNotNull(poses[setup])
                    val pressing = progressed(initial, current, frames[index].timestamp - frames[setup].timestamp, scale) &&
                        (current.wrist.y - current.shoulder.y) / scale >= MIN_HAND_BELOW_SHOULDER &&
                        (current.pelvis.y - current.wrist.y) / scale <= MAX_PELVIS_BELOW_HAND &&
                        current.elbowAngle >= MIN_PRESS_ANGLE_DEGREES && footNear(current, scale) && coherentHand
                    if (!pressing) {
                        resetConfirmation()
                    } else {
                        if (confirmationStart < 0 || lastObserved != index - 1) confirmationStart = index
                        if (frames[index].timestamp - frames[confirmationStart].timestamp >= MIN_CONFIRMATION_MS) {
                            findings += Finding(
                                UUID.randomUUID().toString(), frames[setup].attemptId, "MANTLE",
                                frames[setup].frameIndex, frames[index].frameIndex
                            )
                            completedHand = current.wrist
                            historyStart = index + 1
                            resetConfirmation()
                        }
                    }
                    lastObserved = index
                }
            }
        }
        // Adjacent limb/press phases can describe one mantle. Other technique types stay independent.
        val timestamps = frames.associate { it.frameIndex to it.timestamp }
        val merged = mutableListOf<Finding>()
        for (finding in findings.sortedBy { it.startFrameIndex }) {
            val previous = merged.lastOrNull()
            if (previous != null && previous.attemptId == finding.attemptId &&
                timestamps.getValue(finding.startFrameIndex) - timestamps.getValue(previous.endFrameIndex) <= MAX_MERGE_GAP_MS) {
                merged[merged.lastIndex] = previous.copy(endFrameIndex = maxOf(previous.endFrameIndex, finding.endFrameIndex))
            } else merged += finding
        }
        return merged
    }

    private fun progressed(initial: Observation, current: Observation, elapsedMs: Long, scale: Float): Boolean {
        val relativeRise = ((initial.pelvis.y - initial.wrist.y) - (current.pelvis.y - current.wrist.y)) / scale
        return elapsedMs >= MIN_SETUP_MS &&
            (initial.pelvis.y - initial.wrist.y) / scale >= MIN_INITIAL_PELVIS_BELOW_HAND &&
            initial.elbowAngle < MAX_SETUP_ANGLE_DEGREES &&
            relativeRise >= MIN_RELATIVE_BODY_RISE && (initial.pelvis.y - current.pelvis.y) / scale >= MIN_IMAGE_BODY_RISE &&
            current.elbowAngle - initial.elbowAngle >= MIN_ELBOW_EXTENSION_DEGREES &&
            (footNear(initial, scale) || (initial.ankle.y - current.ankle.y) / scale >= MIN_FOOT_RISE)
    }

    private fun footNear(pose: Observation, scale: Float) =
        abs(pose.ankle.x - pose.wrist.x) / scale <= MAX_FOOT_HORIZONTAL_DISTANCE &&
            abs(pose.ankle.y - pose.wrist.y) / scale <= MAX_FOOT_VERTICAL_DISTANCE

    private fun observe(
        measurements: PoseMeasurements, index: Int, arm: Int, leg: Int, wrist: PoseMeasurements.Point?
    ): Observation? {
        wrist ?: return null
        val pelvis = measurements.pelvisAt(index) ?: return null
        val shoulder = measurements.landmarkAt(index, LEFT_SHOULDER + arm) ?: return null
        val elbow = estimatedArmPoint(measurements, index, LEFT_ELBOW + arm) ?: return null
        val ankle = measurements.landmarkAt(index, LEFT_ANKLE + leg) ?: return null
        val angle = measurements.jointAngle(shoulder, elbow, wrist)
        if (!angle.isFinite()) return null
        return Observation(wrist, shoulder, pelvis, ankle, angle)
    }

    // Visibility measures occlusion, not coordinate accuracy. Require strong presence and sequence corroboration.
    private fun estimatedArmPoint(measurements: PoseMeasurements, index: Int, id: Int) = measurements.landmarkAt(
        index, id, minVisibility = MIN_ARM_VISIBILITY, minPresence = MIN_ARM_PRESENCE
    )

    private fun smoothWrists(frames: List<PoseFrame>, measurements: PoseMeasurements, arm: Int): List<PoseMeasurements.Point?> {
        val raw = frames.indices.map { estimatedArmPoint(measurements, it, LEFT_WRIST + arm) }
        var start = 0
        return frames.indices.map { index ->
            if (index > 0 && !measurements.isFrameSequenceContinuous(index - 1, index)) start = index
            while (start < index && frames[index].timestamp - frames[start].timestamp > SMOOTHING_WINDOW_MS) start++
            raw[index]?.let {
                val window = (start..index).mapNotNull { raw[it] }
                PoseMeasurements.Point(median(window.map { point -> point.x }), median(window.map { point -> point.y }))
            }
        }
    }

    private fun median(values: List<Float>) = values.sorted().let { it[it.size / 2] }

    private data class Observation(
        val wrist: PoseMeasurements.Point, val shoulder: PoseMeasurements.Point,
        val pelvis: PoseMeasurements.Point, val ankle: PoseMeasurements.Point, val elbowAngle: Double
    )

    private companion object {
        // Engineering estimates, tuned with labelled positive/negative clips; see workspace evidence register.
        const val MIN_ARM_VISIBILITY = 0f // Finite model estimates may be occluded; NaN is still rejected.
        const val MIN_ARM_PRESENCE = 0.9f
        const val SMOOTHING_WINDOW_MS = 300L
        const val OBSERVATION_WINDOW_MS = 200L
        const val MIN_SETUP_MS = 300L
        const val MIN_CONFIRMATION_MS = 250L
        const val MAX_PENDING_GAP_MS = 150L
        const val MAX_SEQUENCE_MS = 5_000L
        const val MAX_MERGE_GAP_MS = 500L
        const val MAX_HAND_EXCURSION = 0.25f // Distances below are torso lengths.
        const val MIN_INITIAL_PELVIS_BELOW_HAND = 0.15f
        const val MIN_RELATIVE_BODY_RISE = 0.5f
        const val MIN_IMAGE_BODY_RISE = 0.3f
        const val MIN_HAND_BELOW_SHOULDER = 0.2f
        const val MAX_PELVIS_BELOW_HAND = 0.15f
        const val MAX_SETUP_ANGLE_DEGREES = 150.0
        const val MIN_PRESS_ANGLE_DEGREES = 135.0
        const val MIN_ELBOW_EXTENSION_DEGREES = 25.0
        const val MIN_FOOT_RISE = 0.5f
        const val MAX_FOOT_HORIZONTAL_DISTANCE = 1.2f
        const val MAX_FOOT_VERTICAL_DISTANCE = 1f
        const val REARM_WRIST_DISTANCE = 0.5f
        const val LEFT_SHOULDER = 11
        const val LEFT_ELBOW = 13
        const val LEFT_WRIST = 15
        const val LEFT_ANKLE = 27
    }
}
