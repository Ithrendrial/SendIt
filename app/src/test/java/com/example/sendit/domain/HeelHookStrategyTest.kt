package com.example.sendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeelHookStrategyTest {
    // A near-hip heel stays placed while a bent leg accompanies pelvis movement towards it.
    @Test
    fun classify_returnsOnePossibleHeelHookOnEitherLeg() {
        for (rightLeg in listOf(false, true)) {
            val frames = hookSequence(rightLeg)

            val findings = HeelHookStrategy().classify(frames, ASPECT_RATIO)

            assertEquals(1, findings.size)
            val finding = findings.single()
            assertEquals("HEEL_HOOK", finding.type)
            assertEquals(ATTEMPT_ID, finding.attemptId)
            assertEquals(frames.first().frameIndex, finding.startFrameIndex)
            assertEquals(frames.last().frameIndex, finding.endFrameIndex)
        }
    }

    // Height, a bent knee and body movement alone should not label this toe-down raised-foot example.
    @Test
    fun classify_rejectsARaisedFootWithoutHeelOrientationEvidence() {
        val frames = hookSequence().map { frame ->
            val coordinates = frame.coordinates.copyOf()
            coordinates[LEFT_FOOT_INDEX * PoseFrame.VALUES_PER_LANDMARK + 1] = 0.66f
            frame.copy(coordinates = coordinates)
        }

        assertTrue(HeelHookStrategy().classify(frames, ASPECT_RATIO).isEmpty())
    }

    // A reliable ankle is insufficient when the actual heel becomes unavailable mid-sequence.
    @Test
    fun classify_doesNotBridgeMissingHeelEvidence() {
        val frames = hookSequence().map { frame ->
            if (frame.timestamp != 750L) frame else {
                val coordinates = frame.coordinates.copyOf()
                coordinates[LEFT_HEEL * PoseFrame.VALUES_PER_LANDMARK + PRESENCE_OFFSET] = Float.NaN
                frame.copy(coordinates = coordinates)
            }
        }

        assertTrue(HeelHookStrategy().classify(frames, ASPECT_RATIO).isEmpty())
    }

    // Mirroring also swaps anatomical landmark IDs, exercising the opposite leg's complete geometry.
    private fun hookSequence(rightLeg: Boolean = false) =
        (0L..DURATION_MS step SAMPLE_INTERVAL_MS).mapIndexed { index, timestamp ->
            val coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) { Float.NaN }
            val progress = timestamp.toFloat() / DURATION_MS
            val pelvisX = 0.5f - TORSO_LENGTH * 0.3f * progress
            val pelvisY = 0.65f - TORSO_LENGTH * 0.1f * progress
            fun landmark(id: Int, x: Float, y: Float) {
                val mirroredId = if (!rightLeg) id else if (id % 2 == 1) id + 1 else id - 1
                val screenX = if (rightLeg) 1f - x else x
                floatArrayOf(screenX, y, 0f, 1f, 1f).copyInto(coordinates, mirroredId * PoseFrame.VALUES_PER_LANDMARK)
            }
            landmark(11, pelvisX - 0.05f, pelvisY - TORSO_LENGTH)
            landmark(12, pelvisX + 0.05f, pelvisY - TORSO_LENGTH)
            landmark(23, pelvisX - 0.05f, pelvisY)
            landmark(24, pelvisX + 0.05f, pelvisY)
            landmark(25, 0.35f, 0.45f)
            landmark(26, pelvisX + 0.05f, 0.8f)
            landmark(27, 0.27f, 0.58f)
            landmark(28, pelvisX + 0.05f, 0.95f)
            landmark(LEFT_HEEL, 0.25f, 0.6f)
            landmark(30, pelvisX + 0.05f, 0.97f)
            landmark(LEFT_FOOT_INDEX, 0.28f, 0.55f)
            landmark(32, pelvisX + 0.1f, 0.97f)
            val frameIndex = FIRST_FRAME_INDEX + index
            PoseFrame("heel-frame-$frameIndex", ATTEMPT_ID, frameIndex, timestamp, coordinates)
        }

    private companion object {
        const val ATTEMPT_ID = "heel-hook-attempt"
        const val ASPECT_RATIO = 1f
        const val TORSO_LENGTH = 0.2f
        const val DURATION_MS = 1_500L
        const val SAMPLE_INTERVAL_MS = 250L
        const val FIRST_FRAME_INDEX = 10
        const val LEFT_HEEL = 29
        const val LEFT_FOOT_INDEX = 31
        const val PRESENCE_OFFSET = 4
    }
}
