package com.example.sendit.domain

import java.util.UUID

// Pose-only dynamic rise followed by a settled reaching hand; this does not prove flight or a catch.
class DynoStrategy : ClassificationStrategy {
    override fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding> {
        val measurements = PoseMeasurements(frames, aspectRatio)
        val scale = measurements.scale ?: return emptyList()
        val poses = frames.indices.map { index ->
            if (!measurements.isUsable(index)) null else {
                val limbs = LIMB_IDS.map { id ->
                    if (id == LEFT_WRIST || id == RIGHT_WRIST) {
                        measurements.landmarkAt(index, id, MIN_WRIST_VISIBILITY, MIN_WRIST_PRESENCE)
                    } else measurements.landmarkAt(index, id)
                }
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

        // A reach may continue after launch. Short uncertainty resets settling, not the observed launch.
        fun settledEnd(start: Int, wrist: Int, initialWristY: Float): Int? {
            var lastReliable = start
            for (anchorIndex in start until frames.size) {
                if (frames[anchorIndex].timestamp - frames[start].timestamp > MAX_CATCH_WAIT_MS) return null
                if (anchorIndex > start && !measurements.isFrameSequenceContinuous(anchorIndex - 1, anchorIndex)) return null
                val anchor = poses[anchorIndex]
                if (anchor == null) {
                    if (frames[anchorIndex].timestamp - frames[lastReliable].timestamp > MAX_PENDING_GAP_MS) return null
                    continue
                }
                if (frames[anchorIndex].timestamp - frames[lastReliable].timestamp > MAX_PENDING_GAP_MS) return null
                lastReliable = anchorIndex
                if ((initialWristY - anchor.limbs[wrist].y) / scale < MIN_WRIST_RISE) continue
                for (index in anchorIndex + 1 until frames.size) {
                    if (!measurements.isFrameSequenceContinuous(index - 1, index)) return null
                    val current = poses[index] ?: break
                    if (measurements.distance(anchor.limbs[wrist], current.limbs[wrist]) / scale > MAX_STABLE_EXCURSION) break
                    if (frames[index].timestamp - frames[anchorIndex].timestamp >= SETTLING_MS) return index
                }
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
                val footRose = ANKLE_POSITIONS.any {
                    (setup.limbs[it].y - pose.limbs[it].y) / scale >= MIN_ANKLE_RISE
                }
                val reachingWrist = WRIST_POSITIONS.maxBy { setup.limbs[it].y - pose.limbs[it].y }
                val wristRise = (setup.limbs[reachingWrist].y - pose.limbs[reachingWrist].y) / scale
                if (rise < MIN_PELVIS_RISE || speed < MIN_UPWARD_SPEED || !footRose || wristRise < MIN_WRIST_RISE) continue
                eventEnd = settledEnd(target, reachingWrist, setup.limbs[reachingWrist].y)
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
        const val MIN_UPWARD_SPEED = 2f // Torso lengths per second across the launch.
        const val MIN_ANKLE_RISE = 0.25f // At least one observed foot rises; this does not prove release.
        const val MIN_WRIST_RISE = 0.5f // At least one reaching hand; supported dynamic moves are allowed.
        const val MAX_CATCH_WAIT_MS = 800L
        const val MAX_PENDING_GAP_MS = 150L
        const val MIN_WRIST_VISIBILITY = 0f // Occluded model estimates need strong presence and motion corroboration.
        const val MIN_WRIST_PRESENCE = 0.9f
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val MILLIS_PER_SECOND = 1_000f
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        val LIMB_IDS = listOf(15, 16, 27, 28)
        val ANKLE_POSITIONS = 2..3
        val WRIST_POSITIONS = 0..1 // First two entries of LIMB_IDS.
    }
}
