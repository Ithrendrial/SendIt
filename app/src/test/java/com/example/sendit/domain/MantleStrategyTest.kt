package com.example.sendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MantleStrategyTest {
    // A retained hand, extending arm and rising body precede a foot arriving near the hand.
    @Test
    fun classify_identifiesOnePossibleMantleOnEitherSideDespiteAWristSpike() {
        for (rightArm in listOf(false, true)) {
            val frames = mantleSequence(rightArm)

            val findings = MantleStrategy().classify(frames, ASPECT_RATIO)

            assertEquals(1, findings.size)
            val finding = findings.single()
            assertEquals("MANTLE", finding.type)
            assertEquals(ATTEMPT_ID, finding.attemptId)
            val start = frames.single { it.frameIndex == finding.startFrameIndex }.timestamp
            val end = frames.single { it.frameIndex == finding.endFrameIndex }.timestamp
            assertTrue(start in 0L..300L) // Original setup frames, not a list-relative index.
            assertTrue(end in 2_000L..DURATION_MS) // Include foot approach and observed settling.
        }
    }

    // Rising past a low hand without bringing a foot up, or pulling on an overhead hand, is insufficient.
    @Test
    fun classify_rejectsSequencesWithoutTheSupportedPressAndFootApproach() {
        for (overheadHand in listOf(false, true)) {
            val frames = mantleSequence().map { frame ->
                val coordinates = frame.coordinates.copyOf()
                if (overheadHand) {
                    coordinates[LEFT_WRIST * PoseFrame.VALUES_PER_LANDMARK + 1] = 0.15f
                } else {
                    coordinates[LEFT_ANKLE * PoseFrame.VALUES_PER_LANDMARK + 1] = INITIAL_ANKLE_Y
                }
                frame.copy(coordinates = coordinates)
            }

            assertTrue(MantleStrategy().classify(frames, ASPECT_RATIO).isEmpty())
        }
    }

    // The hand cannot be assumed to remain supporting the climber while the press is unobserved.
    @Test
    fun classify_doesNotBridgeMissingSupportingWristEvidence() {
        val frames = mantleSequence().map { frame ->
            if (frame.timestamp !in 600L..1_600L) frame else {
                val coordinates = frame.coordinates.copyOf()
                coordinates[LEFT_WRIST * PoseFrame.VALUES_PER_LANDMARK + PRESENCE_OFFSET] = Float.NaN
                frame.copy(coordinates = coordinates)
            }
        }

        assertTrue(MantleStrategy().classify(frames, ASPECT_RATIO).isEmpty())
    }

    // Torso stays the same length. Press: 300–1300 ms; foot approach: 1300–2100 ms.
    // Unused arm landmarks are unavailable; one visible supporting arm is sufficient.
    private fun mantleSequence(rightArm: Boolean = false) =
        (0L..DURATION_MS step SAMPLE_INTERVAL_MS).mapIndexed { index, timestamp ->
            val coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) { Float.NaN }
            fun landmark(id: Int, x: Float, y: Float) {
                val mirroredId = if (!rightArm) id else if (id % 2 == 1) id + 1 else id - 1
                val screenX = if (rightArm) 1f - x else x
                floatArrayOf(screenX, y, 0f, 1f, 1f).copyInto(coordinates, mirroredId * PoseFrame.VALUES_PER_LANDMARK)
            }
            val press = ((timestamp - 300L) / 1_000f).coerceIn(0f, 1f)
            val footApproach = ((timestamp - 1_300L) / 800f).coerceIn(0f, 1f)
            val pelvisY = 0.65f - TORSO_LENGTH * press
            landmark(11, 0.45f, pelvisY - TORSO_LENGTH)
            landmark(12, 0.55f, pelvisY - TORSO_LENGTH)
            landmark(23, 0.45f, pelvisY)
            landmark(24, 0.55f, pelvisY)
            landmark(13, 0.30f + 0.125f * press, 0.50f - 0.10f * press)
            val wristJitter = if (timestamp == 700L) TORSO_LENGTH * 0.25f else 0f
            landmark(LEFT_WRIST, 0.4f + wristJitter, SUPPORT_Y)
            landmark(LEFT_ANKLE, 0.4f, INITIAL_ANKLE_Y - (INITIAL_ANKLE_Y - SUPPORT_Y) * footApproach)
            landmark(28, 0.55f, INITIAL_ANKLE_Y)
            val frameIndex = FIRST_FRAME_INDEX + index
            PoseFrame("mantle-frame-$frameIndex", ATTEMPT_ID, frameIndex, timestamp, coordinates)
        }

    private companion object {
        const val ATTEMPT_ID = "mantle-attempt"
        const val ASPECT_RATIO = 1f
        const val TORSO_LENGTH = 0.2f
        const val SUPPORT_Y = 0.55f
        const val INITIAL_ANKLE_Y = 0.95f
        const val DURATION_MS = 2_500L
        const val SAMPLE_INTERVAL_MS = 100L
        const val FIRST_FRAME_INDEX = 30
        const val LEFT_WRIST = 15
        const val LEFT_ANKLE = 27
        const val PRESENCE_OFFSET = 4
    }
}
