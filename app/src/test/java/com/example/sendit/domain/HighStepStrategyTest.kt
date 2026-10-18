package com.example.sendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighStepStrategyTest {
    // Either leg can make a compact high placement or a wider placement followed by a step-up.
    @Test
    fun classify_identifiesOneHighStepOnEitherSideDespiteAnAnkleSpike() {
        for (rightLeg in listOf(false, true)) for (ankleLift in listOf(0.35f, 0.4f)) for (placementOnly in listOf(false, true)) {
            // The higher placement keeps lifting after entering the candidate height range.
            val frames = stepSequence(
                rightLeg, bodyRises = !placementOnly, legExtends = !placementOnly,
                ankleLift = ankleLift, ankleX = if (placementOnly) 0.3f else 0.2f
            )

            val findings = HighStepStrategy().classify(frames, ASPECT_RATIO)

            assertEquals("rightLeg=$rightLeg, ankleLift=$ankleLift, placementOnly=$placementOnly", 1, findings.size)
            val finding = findings.single()
            assertEquals("HIGH_STEP", finding.type)
            assertEquals(ATTEMPT_ID, finding.attemptId)
            val start = frames.single { it.frameIndex == finding.startFrameIndex }.timestamp
            val end = frames.single { it.frameIndex == finding.endFrameIndex }.timestamp
            assertTrue(start in 400L..700L) // Original frame indices locate the observed placement.
            assertTrue(end in 700L..DURATION_MS) // Confirm settled high placement or subsequent progression.
        }
    }

    // A low foot, an already-raised static pose and a never-settled moving foot are insufficient.
    @Test
    fun classify_rejectsSequencesWithoutObservedHighFootPlacement() {
        for (variant in listOf("low-foot", "already-raised", "moving-foot")) {
            val original = stepSequence()
            val raisedPose = original.first { it.timestamp == 600L }.coordinates
            val frames = original.map { frame ->
                val coordinates = if (variant == "already-raised") raisedPose.copyOf() else frame.coordinates.copyOf()
                val ankle = LEFT_ANKLE * PoseFrame.VALUES_PER_LANDMARK
                if (variant == "low-foot") {
                    val hipY = coordinates[LEFT_HIP * PoseFrame.VALUES_PER_LANDMARK + 1]
                    coordinates[ankle + 1] = hipY + TORSO_LENGTH * 1.5f
                }
                if (variant == "moving-foot") {
                    coordinates[ankle] += TORSO_LENGTH * (frame.timestamp - 500L).coerceAtLeast(0L) / 100f * 0.3f
                }
                frame.copy(coordinates = coordinates)
            }

            assertTrue(variant, HighStepStrategy().classify(frames, ASPECT_RATIO).isEmpty())
        }
    }

    // A missing ankle during the lift cannot be reconstructed from a later high-foot posture.
    @Test
    fun classify_doesNotInferPlacementAcrossMissingAnkleEvidence() {
        val frames = stepSequence().map { frame ->
            if (frame.timestamp !in 200L..800L) frame else {
                val coordinates = frame.coordinates.copyOf()
                coordinates[LEFT_ANKLE * PoseFrame.VALUES_PER_LANDMARK + PRESENCE_OFFSET] = Float.NaN
                frame.copy(coordinates = coordinates)
            }
        }

        assertTrue(HighStepStrategy().classify(frames, ASPECT_RATIO).isEmpty())
    }

    // Lift: 200–600 ms. Step up: 900–1800 ms. Only the selected leg is required.
    private fun stepSequence(
        rightLeg: Boolean = false,
        bodyRises: Boolean = true,
        legExtends: Boolean = true,
        ankleLift: Float = 0.35f,
        ankleX: Float = 0.3f
    ) =
        (0L..DURATION_MS step SAMPLE_INTERVAL_MS).mapIndexed { index, timestamp ->
            val coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) { Float.NaN }
            fun landmark(id: Int, x: Float, y: Float) {
                val mirroredId = if (!rightLeg) id else if (id % 2 == 1) id + 1 else id - 1
                val screenX = if (rightLeg) 1f - x else x
                floatArrayOf(screenX, y, 0f, 1f, 1f).copyInto(coordinates, mirroredId * PoseFrame.VALUES_PER_LANDMARK)
            }
            val lift = ((timestamp - 200L) / 400f).coerceIn(0f, 1f)
            val stepUp = ((timestamp - 900L) / 900f).coerceIn(0f, 1f)
            val hipY = 0.65f - if (bodyRises) TORSO_LENGTH * stepUp else 0f
            val ankleY = 0.95f - ankleLift * lift
            landmark(11, 0.45f, hipY - TORSO_LENGTH)
            landmark(12, 0.55f, hipY - TORSO_LENGTH)
            landmark(LEFT_HIP, 0.45f, hipY)
            landmark(24, 0.55f, hipY)
            // Offset a knee from the hip–ankle midpoint; reducing the offset extends the leg.
            val bend = 0.8f * if (legExtends) 1f - stepUp else 1f
            val kneeX = (0.45f + ankleX) / 2f - (ankleY - hipY) * bend
            val kneeY = (hipY + ankleY) / 2f + (ankleX - 0.45f) * bend
            landmark(25, kneeX, kneeY)
            val spike = if (timestamp == 1_000L) TORSO_LENGTH * 0.25f else 0f
            landmark(LEFT_ANKLE, ankleX + spike, ankleY)
            val frameIndex = FIRST_FRAME_INDEX + index
            PoseFrame("high-step-frame-$frameIndex", ATTEMPT_ID, frameIndex, timestamp, coordinates)
        }

    private companion object {
        const val ATTEMPT_ID = "high-step-attempt"
        const val ASPECT_RATIO = 1f
        const val TORSO_LENGTH = 0.2f
        const val SAMPLE_INTERVAL_MS = 100L
        const val DURATION_MS = 2_200L
        const val FIRST_FRAME_INDEX = 40
        const val LEFT_HIP = 23
        const val LEFT_ANKLE = 27
        const val PRESENCE_OFFSET = 4
    }
}
