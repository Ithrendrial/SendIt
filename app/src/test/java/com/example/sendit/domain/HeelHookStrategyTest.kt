package com.example.sendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeelHookStrategyTest {
    // A raised heel settles despite one tracking spike, then leg flexion corroborates use.
    @Test
    fun classify_identifiesHeelPlacementAndUseDespiteATrackingSpike() {
        for (rightLeg in listOf(false, true)) {
            val original = hookSequence(rightLeg)
            val frames = original.map { frame ->
                val coordinates = frame.coordinates.copyOf()
                // Keep the body stationary: stabilising hooks need not pull the pelvis closer.
                for (id in listOf(11, 12, 23, 24)) {
                    val offset = id * PoseFrame.VALUES_PER_LANDMARK
                    coordinates[offset] = original.first().coordinates[offset]
                    coordinates[offset + 1] = original.first().coordinates[offset + 1]
                }
                val hip = (if (rightLeg) 24 else 23) * PoseFrame.VALUES_PER_LANDMARK
                val knee = (if (rightLeg) 26 else 25) * PoseFrame.VALUES_PER_LANDMARK
                val ankle = (if (rightLeg) 28 else 27) * PoseFrame.VALUES_PER_LANDMARK
                if (frame.timestamp < 800L) {
                    coordinates[knee] = (coordinates[hip] + coordinates[ankle]) / 2f
                    coordinates[knee + 1] = (coordinates[hip + 1] + coordinates[ankle + 1]) / 2f
                }
                if (frame.timestamp == 1_000L) {
                    val heel = (if (rightLeg) 30 else LEFT_HEEL) * PoseFrame.VALUES_PER_LANDMARK
                    coordinates[heel] += TORSO_LENGTH * 0.25f
                }
                if (frame.timestamp >= 1_400L) {
                    val toe = (if (rightLeg) 32 else LEFT_FOOT_INDEX) * PoseFrame.VALUES_PER_LANDMARK
                    coordinates[toe + 1] = 0.66f // Toe-up placement can roll to a different orientation.
                }
                frame.copy(coordinates = coordinates)
            }

            val findings = HeelHookStrategy().classify(frames, ASPECT_RATIO)

            assertEquals(1, findings.size)
            val finding = findings.single()
            assertEquals("HEEL_HOOK", finding.type)
            assertEquals(ATTEMPT_ID, finding.attemptId)
            val start = frames.single { it.frameIndex == finding.startFrameIndex }.timestamp
            val end = frames.single { it.frameIndex == finding.endFrameIndex }.timestamp
            assertTrue(start in 400L..600L) // Observed placement after the lift.
            assertTrue(end in 1_100L..1_500L) // Confirmation, not the end of all heel use.
        }
    }

    // Height, a bent knee and body movement alone should not label this toe-down raised-foot example.
    @Test
    fun classify_rejectsARaisedFootWithoutHeelOrientationEvidence() {
        val frames = hookSequence().map { frame ->
            val coordinates = frame.coordinates.copyOf()
            coordinates[LEFT_FOOT_INDEX * PoseFrame.VALUES_PER_LANDMARK + 1] =
                coordinates[LEFT_HEEL * PoseFrame.VALUES_PER_LANDMARK + 1] + 0.06f
            frame.copy(coordinates = coordinates)
        }

        assertTrue(HeelHookStrategy().classify(frames, ASPECT_RATIO).isEmpty())
    }

    // A missing heel interrupts settling; shorter spans on either side must not be added together.
    @Test
    fun classify_doesNotCountMissingHeelTimeAsContinuousSettling() {
        val frames = hookSequence().map { frame ->
            if (frame.timestamp != 1_000L) frame else {
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
            val lowering = TORSO_LENGTH * 1.5f * (1f - ((timestamp - 200L) / 300f).coerceIn(0f, 1f))
            val kneeX = if (timestamp < 800L) (pelvisX - 0.05f + 0.27f) / 2f else 0.35f
            val kneeY = if (timestamp < 800L) (pelvisY + 0.58f + lowering) / 2f else 0.45f
            landmark(25, kneeX, kneeY)
            landmark(26, pelvisX + 0.05f, 0.8f)
            landmark(27, 0.27f, 0.58f + lowering)
            landmark(28, pelvisX + 0.05f, 0.95f)
            landmark(LEFT_HEEL, 0.25f, 0.6f + lowering)
            landmark(30, pelvisX + 0.05f, 0.97f)
            landmark(LEFT_FOOT_INDEX, 0.28f, 0.55f + lowering)
            landmark(32, pelvisX + 0.1f, 0.97f)
            val frameIndex = FIRST_FRAME_INDEX + index
            PoseFrame("heel-frame-$frameIndex", ATTEMPT_ID, frameIndex, timestamp, coordinates)
        }

    private companion object {
        const val ATTEMPT_ID = "heel-hook-attempt"
        const val ASPECT_RATIO = 1f
        const val TORSO_LENGTH = 0.2f
        const val DURATION_MS = 1_500L
        const val SAMPLE_INTERVAL_MS = 100L
        const val FIRST_FRAME_INDEX = 10
        const val LEFT_HEEL = 29
        const val LEFT_FOOT_INDEX = 31
        const val PRESENCE_OFFSET = 4
    }
}
