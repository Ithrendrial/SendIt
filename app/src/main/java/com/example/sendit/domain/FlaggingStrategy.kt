package com.example.sendit.domain

import java.util.UUID
import kotlin.math.abs

// Pose-only outside-flag candidates. Stable ankles suggest support but cannot establish loading.
class FlaggingStrategy : ClassificationStrategy {
    override fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding> {
        val measurements = PoseMeasurements(frames, aspectRatio)
        val scale = measurements.scale ?: return emptyList()
        val findings = mutableListOf<Finding>()
        var start = -1
        var activeLeg = -1
        var anchor: PoseMeasurements.Point? = null

        fun finish(end: Int) {
            if (start >= 0 && frames[end].timestamp - frames[start].timestamp >= MIN_FLAGGING_MS) {
                findings += Finding(
                    id = UUID.randomUUID().toString(),
                    attemptId = frames[start].attemptId,
                    type = "FLAGGING",
                    startFrameIndex = frames[start].frameIndex,
                    endFrameIndex = frames[end].frameIndex
                )
            }
            start = -1
            activeLeg = -1
            anchor = null
        }

        for (index in frames.indices) {
            val candidate = outsideFlag(measurements, index, scale)
            if (candidate == null) {
                finish(index - 1)
                continue
            }
            val reference = anchor
            if (start >= 0 && (!measurements.isContinuous(index - 1, index) ||
                    candidate.leg != activeLeg || reference == null ||
                    measurements.distance(reference, candidate.supportingAnkle) / scale > MAX_SUPPORT_EXCURSION)) {
                finish(index - 1)
            }
            if (start < 0) {
                start = index
                activeLeg = candidate.leg
                anchor = candidate.supportingAnkle
            }
        }
        finish(frames.lastIndex)
        return findings
    }

    private fun outsideFlag(measurements: PoseMeasurements, index: Int, scale: Float): Candidate? {
        if (!measurements.isUsable(index)) return null
        val hips = HIP_IDS.map { measurements.landmarkAt(index, it) ?: return null }
        val knees = KNEE_IDS.map { measurements.landmarkAt(index, it) ?: return null }
        val ankles = ANKLE_IDS.map { measurements.landmarkAt(index, it) ?: return null }
        if (hips[0].x == hips[1].x) return null // Screen-side direction is ambiguous.
        val pelvisX = (hips[0].x + hips[1].x) / 2f
        for (leg in hips.indices) {
            val otherLeg = 1 - leg
            val direction = if (hips[leg].x > hips[otherLeg].x) 1f else -1f
            val extension = (ankles[leg].x - hips[leg].x) * direction / scale
            val supportOffset = abs(ankles[otherLeg].x - pelvisX) / scale
            if (extension >= MIN_OUTSIDE_EXTENSION && supportOffset <= MAX_SUPPORT_OFFSET &&
                measurements.jointAngle(hips[leg], knees[leg], ankles[leg]) >= MIN_KNEE_ANGLE_DEGREES
            ) return Candidate(leg, ankles[otherLeg])
        }
        return null
    }

    private data class Candidate(val leg: Int, val supportingAnkle: PoseMeasurements.Point)

    private companion object {
        // Provisional engineering estimates; rationale is in the workspace classification evidence.
        const val MIN_OUTSIDE_EXTENSION = 0.75f // Torso lengths outward from the same-side hip.
        const val MAX_SUPPORT_OFFSET = 0.35f // Torso lengths horizontally from the pelvis.
        const val MAX_SUPPORT_EXCURSION = 0.15f // Torso lengths from the interval's ankle anchor.
        const val MIN_KNEE_ANGLE_DEGREES = 150.0
        const val MIN_FLAGGING_MS = 1_000L
        val HIP_IDS = listOf(23, 24)
        val KNEE_IDS = listOf(25, 26)
        val ANKLE_IDS = listOf(27, 28)
    }
}
