package com.example.sendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynoStrategyTest {
    // A moving preparation followed by upward relocation and settled wrists produces one candidate.
    @Test
    fun classify_identifiesOneUpwardRelocationAfterAMovingPreparation() {
        for (support in listOf("none", "hand", "hand-and-foot")) {
            val original = dynoSequence(if (support == "hand-and-foot") 50L else SAMPLE_INTERVAL_MS)
            val frames = original.map { frame ->
                val coordinates = frame.coordinates.copyOf()
                if (frame.timestamp <= 200L) {
                    // Preparatory adjustment exceeds the old stillness tolerance without losing tracking.
                    val adjustment = frame.timestamp / 200f * TORSO_LENGTH * 0.2f
                    for (landmark in listOf(15, 16, 27, 28)) {
                        coordinates[landmark * PoseFrame.VALUES_PER_LANDMARK] += adjustment
                    }
                }
                if (support != "none") {
                    // A supported dynamic reach can keep one hand on its original hold.
                    val wrist = 15 * PoseFrame.VALUES_PER_LANDMARK
                    coordinates[wrist] = original.first().coordinates[wrist]
                    coordinates[wrist + 1] = original.first().coordinates[wrist + 1]
                }
                if (support == "hand-and-foot") {
                    val ankle = LEFT_ANKLE * PoseFrame.VALUES_PER_LANDMARK
                    coordinates[ankle] = original.first().coordinates[ankle]
                    coordinates[ankle + 1] = original.first().coordinates[ankle + 1]
                }
                if (support == "hand-and-foot" && frame.timestamp == 800L) {
                    // Brief uncertainty after launch resets catch settling, not the observed launch.
                    coordinates[LEFT_ANKLE * PoseFrame.VALUES_PER_LANDMARK + PRESENCE_OFFSET] = Float.NaN
                }
                frame.copy(coordinates = coordinates)
            }

            val findings = DynoStrategy().classify(frames, ASPECT_RATIO)

            assertEquals(1, findings.size)
            val finding = findings.single()
            assertEquals("DYNO", finding.type)
            assertEquals(ATTEMPT_ID, finding.attemptId)
            val start = frames.single { it.frameIndex == finding.startFrameIndex }.timestamp
            val end = frames.single { it.frameIndex == finding.endFrameIndex }.timestamp
            assertTrue(start in 100L..300L) // Start near launch, rather than at the target reach.
            assertTrue(end in 800L..1_200L) // Include observed settling after the reach at 600 ms.
        }
    }

    // A supported reach with both feet planted is insufficient, despite fast body/hand motion.
    @Test
    fun classify_rejectsAFastReachWithBothFeetPlanted() {
        val frames = dynoSequence()
        val ankleOffset = LEFT_ANKLE * PoseFrame.VALUES_PER_LANDMARK
        val initialAnkleY = frames.first().coordinates[ankleOffset + 1]
        val supportedReach = frames.map { frame ->
            val coordinates = frame.coordinates.copyOf()
            coordinates[ankleOffset + 1] = initialAnkleY
            val otherAnkle = 28 * PoseFrame.VALUES_PER_LANDMARK
            coordinates[otherAnkle + 1] = frames.first().coordinates[otherAnkle + 1]
            frame.copy(coordinates = coordinates)
        }

        assertTrue(DynoStrategy().classify(supportedReach, ASPECT_RATIO).isEmpty())
    }

    // Do not infer a launch through an unobserved ankle, even when the surrounding poses fit.
    @Test
    fun classify_rejectsALaunchWithMissingAnkleEvidence() {
        val interrupted = dynoSequence().map { frame ->
            if (frame.timestamp != 400L) frame else {
                val coordinates = frame.coordinates.copyOf()
                coordinates[LEFT_ANKLE * PoseFrame.VALUES_PER_LANDMARK + PRESENCE_OFFSET] = Float.NaN
                frame.copy(coordinates = coordinates)
            }
        }

        assertTrue(DynoStrategy().classify(interrupted, ASPECT_RATIO).isEmpty())
    }

    // Setup ends at 200 ms. The pelvis and all four limb endpoints rise one torso length by 600 ms.
    // The target wrists then remain still.
    private fun dynoSequence(sampleIntervalMs: Long = SAMPLE_INTERVAL_MS) = (0L..1_200L step sampleIntervalMs).mapIndexed { index, timestamp ->
        val coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) { Float.NaN }
        val rise = (timestamp - 200L).coerceIn(0L, 400L) / 400f * TORSO_LENGTH
        fun landmark(id: Int, x: Float, y: Float) {
            floatArrayOf(x, y - rise, 0f, 1f, 1f).copyInto(coordinates, id * PoseFrame.VALUES_PER_LANDMARK)
        }
        landmark(11, 0.45f, 0.7f - TORSO_LENGTH)
        landmark(12, 0.55f, 0.7f - TORSO_LENGTH)
        landmark(23, 0.45f, 0.7f)
        landmark(24, 0.55f, 0.7f)
        landmark(15, 0.35f, 0.4f)
        landmark(16, 0.65f, 0.4f)
        landmark(LEFT_ANKLE, 0.45f, 0.95f)
        landmark(28, 0.55f, 0.95f)
        val frameIndex = FIRST_FRAME_INDEX + index
        PoseFrame("dyno-frame-$frameIndex", ATTEMPT_ID, frameIndex, timestamp, coordinates)
    }

    private companion object {
        const val ATTEMPT_ID = "dyno-attempt"
        const val ASPECT_RATIO = 1f
        const val TORSO_LENGTH = 0.2f
        const val SAMPLE_INTERVAL_MS = 100L
        const val FIRST_FRAME_INDEX = 10
        const val LEFT_ANKLE = 27
        const val PRESENCE_OFFSET = 4
    }
}
