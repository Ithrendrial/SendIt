package com.example.sendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlaggingStrategyTest {
    // Either screen direction can contain an outside flag; preserve the stored frame boundaries.
    @Test
    fun classify_returnsOneCandidateForASustainedOutsideFlagOnEitherSide() {
        for (mirrored in listOf(false, true)) {
            val frames = sequence(mirrored = mirrored)

            val findings = FlaggingStrategy().classify(frames, ASPECT_RATIO)

            assertEquals(1, findings.size)
            val finding = findings.single()
            assertEquals("FLAGGING", finding.type)
            assertEquals(ATTEMPT_ID, finding.attemptId)
            assertEquals(frames.first().frameIndex, finding.startFrameIndex)
            assertEquals(frames.last().frameIndex, finding.endFrameIndex)
        }
    }

    // Two laterally extended legs do not establish the selected one-foot-support configuration.
    @Test
    fun classify_doesNotLabelASymmetricWideStance() {
        val frames = sequence().map { frame ->
            val coordinates = frame.coordinates.copyOf()
            setLandmark(coordinates, RIGHT_KNEE, 0.7f, 0.7f)
            setLandmark(coordinates, RIGHT_ANKLE, 0.85f, 0.9f)
            frame.copy(coordinates = coordinates)
        }

        assertTrue(FlaggingStrategy().classify(frames, ASPECT_RATIO).isEmpty())
    }

    // Unavailable ankle confidence must break the run, leaving two intervals too short to qualify.
    @Test
    fun classify_doesNotJoinShortRunsAcrossMissingAnkleEvidence() {
        val frames = sequence()
        val midpoint = frames.size / 2
        val occluded = frames.mapIndexed { index, frame ->
            if (index != midpoint) frame else {
                val coordinates = frame.coordinates.copyOf()
                coordinates[LEFT_ANKLE * PoseFrame.VALUES_PER_LANDMARK + PRESENCE_OFFSET] = Float.NaN
                frame.copy(coordinates = coordinates)
            }
        }
        assertTrue(FlaggingStrategy().classify(occluded, ASPECT_RATIO).isEmpty())
    }

    // A straight left leg extends outside; the opposite ankle stays near the pelvis horizontally.
    // Mirroring reverses image x, including hip ordering, without changing anatomical landmark IDs.
    private fun sequence(mirrored: Boolean = false) =
        (0L..2_000L step SAMPLE_INTERVAL_MS).mapIndexed { index, timestamp ->
            val coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) { Float.NaN }
            fun landmark(id: Int, x: Float, y: Float) {
                setLandmark(coordinates, id, if (mirrored) 1f - x else x, y)
            }
            landmark(11, 0.45f, 0.5f - TORSO_LENGTH)
            landmark(12, 0.55f, 0.5f - TORSO_LENGTH)
            landmark(23, 0.45f, 0.5f)
            landmark(24, 0.55f, 0.5f)
            landmark(25, 0.3f, 0.7f)
            landmark(RIGHT_KNEE, 0.54f, 0.7f)
            landmark(LEFT_ANKLE, 0.15f, 0.9f)
            landmark(RIGHT_ANKLE, 0.52f, 0.9f)
            val frameIndex = FIRST_FRAME_INDEX + index
            PoseFrame("frame-$frameIndex", ATTEMPT_ID, frameIndex, timestamp, coordinates)
        }

    private fun setLandmark(coordinates: FloatArray, id: Int, x: Float, y: Float) {
        floatArrayOf(x, y, 0f, 1f, 1f).copyInto(coordinates, id * PoseFrame.VALUES_PER_LANDMARK)
    }

    private companion object {
        const val ATTEMPT_ID = "flagging-attempt"
        const val ASPECT_RATIO = 1f
        const val TORSO_LENGTH = 0.2f
        const val FIRST_FRAME_INDEX = 20
        const val SAMPLE_INTERVAL_MS = 250L
        const val RIGHT_KNEE = 26
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
        const val PRESENCE_OFFSET = 4
    }
}
