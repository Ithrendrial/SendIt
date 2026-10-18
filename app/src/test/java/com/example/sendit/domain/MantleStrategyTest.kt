package com.example.sendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MantleStrategyTest {
    // Foot-to-hand movement can precede the press, as in a high-foot/heel-hook combination.
    @Test
    fun classify_identifiesMantlePhasesOnEitherSideDespiteAWristSpike() {
        for (rightArm in listOf(false, true)) {
            for (variant in listOf("press", "raised-foot", "pull-to-press", "occluded-press")) {
                val footAlreadyRaised = variant == "raised-foot" || variant == "occluded-press"
                val frames = mantleSequence(
                    rightArm, startsWithPull = variant == "pull-to-press",
                    sampleIntervalMs = if (variant == "occluded-press") 50L else SAMPLE_INTERVAL_MS
                ).map { frame ->
                    if (!footAlreadyRaised) frame else {
                        val coordinates = frame.coordinates.copyOf()
                        val ankleId = if (rightArm) LEFT_ANKLE + 1 else LEFT_ANKLE
                        coordinates[ankleId * PoseFrame.VALUES_PER_LANDMARK + 1] = SUPPORT_Y + TORSO_LENGTH * 0.05f
                        if (variant == "occluded-press") {
                            // Low visibility means occluded; high presence still supplies a model estimate.
                            for (id in listOf(if (rightArm) 14 else 13, if (rightArm) 16 else LEFT_WRIST)) {
                                coordinates[id * PoseFrame.VALUES_PER_LANDMARK + 3] = 0.02f
                                coordinates[id * PoseFrame.VALUES_PER_LANDMARK + PRESENCE_OFFSET] = 0.95f
                            }
                            coordinates[ankleId * PoseFrame.VALUES_PER_LANDMARK] = if (rightArm) 0.44f else 0.56f
                            coordinates[ankleId * PoseFrame.VALUES_PER_LANDMARK + 1] = SUPPORT_Y + TORSO_LENGTH * 0.75f
                            if (frame.timestamp == 600L) return@map frame.copy(coordinates = floatArrayOf())
                        }
                        frame.copy(coordinates = coordinates)
                    }
                }

                val findings = MantleStrategy().classify(frames, ASPECT_RATIO)

                assertEquals("rightArm=$rightArm, variant=$variant", 1, findings.size)
                val finding = findings.single()
                assertEquals("MANTLE", finding.type)
                assertEquals(ATTEMPT_ID, finding.attemptId)
                val start = frames.single { it.frameIndex == finding.startFrameIndex }.timestamp
                val end = frames.single { it.frameIndex == finding.endFrameIndex }.timestamp
                assertTrue(start in 0L..300L) // Original setup frames, not a list-relative index.
                val expectedConfirmation = if (footAlreadyRaised) 1_000L..1_800L else 2_000L..DURATION_MS
                assertTrue(end in expectedConfirmation) // Body/arm progression and reliable foot settling.
            }
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
    private fun mantleSequence(
        rightArm: Boolean = false, startsWithPull: Boolean = false, sampleIntervalMs: Long = SAMPLE_INTERVAL_MS
    ) =
        (0L..DURATION_MS step sampleIntervalMs).mapIndexed { index, timestamp ->
            val coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) { Float.NaN }
            fun landmark(id: Int, x: Float, y: Float) {
                val mirroredId = if (!rightArm) id else if (id % 2 == 1) id + 1 else id - 1
                val screenX = if (rightArm) 1f - x else x
                floatArrayOf(screenX, y, 0f, 1f, 1f).copyInto(coordinates, mirroredId * PoseFrame.VALUES_PER_LANDMARK)
            }
            val press = ((timestamp - 300L) / 1_000f).coerceIn(0f, 1f)
            val footApproach = ((timestamp - 1_300L) / 800f).coerceIn(0f, 1f)
            // The pull variant begins with the hand above the shoulder and ends in the same press.
            val initialPelvisY = if (startsWithPull) 0.85f else 0.65f
            val pelvisY = initialPelvisY - (initialPelvisY - 0.45f) * press
            landmark(11, 0.45f, pelvisY - TORSO_LENGTH)
            landmark(12, 0.55f, pelvisY - TORSO_LENGTH)
            landmark(23, 0.45f, pelvisY)
            landmark(24, 0.55f, pelvisY)
            val initialElbowY = if (startsWithPull) 0.60f else 0.50f
            landmark(13, 0.30f + 0.125f * press, initialElbowY - (initialElbowY - 0.40f) * press)
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
