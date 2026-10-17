package com.example.sendit.domain

import java.util.UUID

// Pose-only upward relocation followed by settled wrists; this does not prove flight or a catch.
class DynoStrategy : ClassificationStrategy {
    override fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding> {
        val measurements = PoseMeasurements(frames, aspectRatio)
        val scale = measurements.scale ?: return emptyList()
        val poses = frames.indices.map { index ->
            if (!measurements.isUsable(index)) null else {
                val limbs = LIMB_IDS.map { measurements.landmarkAt(index, it) }
                if (limbs.any { it == null }) null else {
                    val leftHip = measurements.landmarkAt(index, LEFT_HIP)!!
                    val rightHip = measurements.landmarkAt(index, RIGHT_HIP)!!
                    Observation((leftHip.y + rightHip.y) / 2f, limbs.filterNotNull())
                }
            }
        }

        fun continuous(previous: Int, current: Int) = poses[previous] != null &&
            poses[current] != null && measurements.isContinuous(previous, current)

        // Require an observed baseline, allowing the preparatory movement that often precedes launch.
        fun hasSetup(end: Int): Boolean {
            var start = end
            while (frames[end].timestamp - frames[start].timestamp < SETUP_MS) {
                if (start == 0 || !continuous(start - 1, start)) return false
                start--
            }
            return true
        }

        fun settledEnd(start: Int): Int? {
            val anchor = poses[start] ?: return null
            for (index in start + 1 until frames.size) {
                if (!continuous(index - 1, index)) return null
                val pose = poses[index] ?: return null
                if (WRIST_POSITIONS.any {
                    measurements.distance(anchor.limbs[it], pose.limbs[it]) / scale > MAX_STABLE_EXCURSION
                }) return null
                if (frames[index].timestamp - frames[start].timestamp >= SETTLING_MS) return index
            }
            return null
        }

        val findings = mutableListOf<Finding>()
        var setupEnd = 0
        while (setupEnd < frames.size) {
            if (!hasSetup(setupEnd)) {
                setupEnd++
                continue
            }
            val setup = poses[setupEnd]!! // hasSetup requires all core and limb observations.
            var eventEnd: Int? = null
            for (target in setupEnd + 1 until frames.size) {
                val elapsedMs = frames[target].timestamp - frames[setupEnd].timestamp
                if (elapsedMs > MAX_LAUNCH_MS || !continuous(target - 1, target)) break
                if (elapsedMs < MIN_LAUNCH_MS) continue
                val pose = poses[target]!! // continuous requires reliable observations on both sides.
                val rise = (setup.pelvisY - pose.pelvisY) / scale
                val speed = rise / (elapsedMs / MILLIS_PER_SECOND)
                val allLimbsMoved = pose.limbs.indices.all {
                    measurements.distance(setup.limbs[it], pose.limbs[it]) / scale >= MIN_LIMB_RELOCATION
                }
                val bothWristsRose = WRIST_POSITIONS.all {
                    (setup.limbs[it].y - pose.limbs[it].y) / scale >= MIN_WRIST_RISE
                }
                if (rise < MIN_PELVIS_RISE || speed < MIN_UPWARD_SPEED || !allLimbsMoved || !bothWristsRose) continue
                eventEnd = settledEnd(target)
                if (eventEnd != null) break
            }
            if (eventEnd != null) {
                findings += Finding(
                    id = UUID.randomUUID().toString(),
                    attemptId = frames[setupEnd].attemptId,
                    type = "DYNO",
                    startFrameIndex = frames[setupEnd].frameIndex,
                    endFrameIndex = frames[eventEnd].frameIndex
                )
                setupEnd = eventEnd + 1 // Consume the sequence so overlapping windows do not duplicate it.
            } else {
                setupEnd++
            }
        }
        return findings
    }

    private data class Observation(val pelvisY: Float, val limbs: List<PoseMeasurements.Point>)

    private companion object {
        // Engineering estimates documented before implementation in the workspace classification evidence.
        const val SETUP_MS = 200L
        const val MIN_LAUNCH_MS = 200L
        const val MAX_LAUNCH_MS = 800L
        const val SETTLING_MS = 200L
        const val MAX_STABLE_EXCURSION = 0.1f // Torso lengths from the initial catch observation.
        const val MIN_PELVIS_RISE = 0.5f // Torso lengths upwards in image coordinates.
        const val MIN_UPWARD_SPEED = 1f // Torso lengths per second across the launch.
        const val MIN_LIMB_RELOCATION = 0.25f // Torso lengths from setup, for each wrist and ankle.
        const val MIN_WRIST_RISE = 0.5f // Torso lengths above each wrist's setup position.
        const val MILLIS_PER_SECOND = 1_000f
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        val LIMB_IDS = listOf(15, 16, 27, 28)
        val WRIST_POSITIONS = 0..1 // First two entries of LIMB_IDS.
    }
}
