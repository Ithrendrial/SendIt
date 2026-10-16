package com.example.sendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbAnalyserTest {
    // Low pelvis movement with ongoing limb movement is a likely hesitation.
    // Findings use the original frame indices, not positions in this test's list.
    @Test
    fun classify_reportsExplorationWithOriginalFrameBoundaries() {
        val findings = ClimbAnalyser.classify(exploration(), SQUARE_ASPECT_RATIO)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals(ATTEMPT_ID, finding.attemptId)
        assertEquals("HESITATION", finding.type)
        assertEquals(FIRST_FRAME_INDEX, finding.startFrameIndex)
        assertEquals(FIRST_FRAME_INDEX + SAMPLE_TIMES.lastIndex, finding.endFrameIndex)
    }

    // A still rest does not become hesitation just because it lasts a long time.
    @Test
    fun classify_doesNotLabelAStillRest() {
        val frames = listOf(0L, 1_000L, 2_000L, 3_000L).mapIndexed { index, time ->
            frame(index, time, wristOffset = 0f)
        }

        assertTrue(ClimbAnalyser.classify(frames, SQUARE_ASPECT_RATIO).isEmpty())
    }

    // Ordinary upward progression is not hesitation even though limbs are moving.
    @Test
    fun classify_doesNotLabelContinuousProgression() {
        val frames = SAMPLE_TIMES.mapIndexed { index, time ->
            frame(index, time, hipRise = TORSO_LENGTH * index * 0.1f)
        }

        assertTrue(ClimbAnalyser.classify(frames, SQUARE_ASPECT_RATIO).isEmpty())
    }

    // The estimated minimum duration is one second, measured from timestamps.
    @Test
    fun classify_requiresAFullSecondOfExploration() {
        val shortExploration = exploration().map { it.copy(timestamp = it.timestamp * 999L / 1_000L) }

        assertTrue(ClimbAnalyser.classify(shortExploration, SQUARE_ASPECT_RATIO).isEmpty())
        assertEquals(1, ClimbAnalyser.classify(exploration(), SQUARE_ASPECT_RATIO).size)
    }

    // The same body-relative movement should qualify at different image proportions and scales.
    // Lateral pelvis speed is 0.08 torso lengths/second, below the provisional 0.1 limit.
    @Test
    fun classify_accountsForAspectRatioAndBodyScale() {
        val views = listOf(1f to TORSO_LENGTH, 0.5f to TORSO_LENGTH / 2f, 2f to TORSO_LENGTH)
        for ((aspectRatio, torsoLength) in views) {
            val frames = exploration(aspectRatio, torsoLength)

            val finding = ClimbAnalyser.classify(frames, aspectRatio).single()

            assertEquals(FIRST_FRAME_INDEX, finding.startFrameIndex)
            assertEquals(FIRST_FRAME_INDEX + SAMPLE_TIMES.lastIndex, finding.endFrameIndex)
        }
    }

    // Same coordinates and total duration, but early movement is faster at uneven timestamps.
    // The remaining slow period is too short to qualify; assuming a fixed frame rate is wrong.
    @Test
    fun classify_usesElapsedTimeForMovementSpeed() {
        val frames = exploration()
        val unevenTimes = listOf(0L, 100L, 200L, 300L, 1_000L)
        val fasterStart = frames.mapIndexed { index, frame -> frame.copy(timestamp = unevenTimes[index]) }

        assertEquals(1, ClimbAnalyser.classify(frames, SQUARE_ASPECT_RATIO).size)
        assertTrue(ClimbAnalyser.classify(fasterStart, SQUARE_ASPECT_RATIO).isEmpty())
    }

    // Missing, low-confidence, or unknown-confidence observations break continuity.
    // The rule must not join the short valid periods on either side into one finding.
    @Test
    fun classify_doesNotBridgeUnavailablePoseEvidence() {
        val frames = exploration()
        val middle = frames[2]
        val unavailableFrames = listOf(
            middle.copy(coordinates = floatArrayOf()),
            withConfidence(middle, visibility = 0.1f, presence = 1f),
            withConfidence(middle, visibility = 1f, presence = 0.1f),
            withConfidence(middle, visibility = Float.NaN, presence = 1f),
            withConfidence(middle, visibility = 1f, presence = Float.NaN)
        )

        for (unavailable in unavailableFrames) {
            val interrupted = frames.toMutableList().apply { this[2] = unavailable }

            assertTrue(ClimbAnalyser.classify(interrupted, SQUARE_ASPECT_RATIO).isEmpty())
        }
    }

    // A collapsed torso cannot provide a usable scale, and empty input has no finding.
    @Test
    fun classify_returnsNoFindingsWithoutUsableBodyMeasurements() {
        val frames = SAMPLE_TIMES.mapIndexed { index, time -> frame(index, time, torsoLength = 0f) }

        assertTrue(ClimbAnalyser.classify(frames, SQUARE_ASPECT_RATIO).isEmpty())
        assertTrue(ClimbAnalyser.classify(emptyList(), SQUARE_ASPECT_RATIO).isEmpty())
    }

    // Builds a one-second sequence: slowly shifting pelvis and alternating wrist positions.
    private fun exploration(
        aspectRatio: Float = SQUARE_ASPECT_RATIO,
        torsoLength: Float = TORSO_LENGTH
    ): List<PoseFrame> = SAMPLE_TIMES.mapIndexed { index, time ->
        frame(index, time, aspectRatio, torsoLength, hipShift = torsoLength * index * 0.02f)
    }

    // Coordinates describe an upright synthetic body, not a real climbing video.
    // x is divided by the aspect ratio so every fixture describes the same image-plane geometry.
    private fun frame(
        index: Int,
        time: Long,
        aspectRatio: Float = SQUARE_ASPECT_RATIO,
        torsoLength: Float = TORSO_LENGTH,
        hipShift: Float = 0f,
        hipRise: Float = 0f,
        wristOffset: Float = if (index % 2 == 0) 0f else torsoLength * 0.15f
    ): PoseFrame {
        val coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) { Float.NaN }
        val hipX = 0.3f + hipShift
        val hipY = 0.6f - hipRise

        fun landmark(landmarkIndex: Int, x: Float, y: Float) {
            val offset = landmarkIndex * PoseFrame.VALUES_PER_LANDMARK
            coordinates[offset] = x / aspectRatio
            coordinates[offset + 1] = y
            coordinates[offset + 2] = 0f
            coordinates[offset + VISIBILITY_OFFSET] = 1f
            coordinates[offset + PRESENCE_OFFSET] = 1f
        }

        landmark(LEFT_SHOULDER, hipX - torsoLength * 0.2f, hipY - torsoLength)
        landmark(RIGHT_SHOULDER, hipX + torsoLength * 0.2f, hipY - torsoLength)
        landmark(LEFT_HIP, hipX - torsoLength * 0.2f, hipY)
        landmark(RIGHT_HIP, hipX + torsoLength * 0.2f, hipY)
        landmark(LEFT_WRIST, hipX - torsoLength * 0.6f + wristOffset, hipY - torsoLength * 0.6f)
        landmark(RIGHT_WRIST, hipX + torsoLength * 0.6f, hipY - torsoLength * 0.6f)
        landmark(LEFT_ANKLE, hipX - torsoLength * 0.3f, hipY + torsoLength)
        landmark(RIGHT_ANKLE, hipX + torsoLength * 0.3f, hipY + torsoLength)

        val frameIndex = FIRST_FRAME_INDEX + index
        return PoseFrame("frame-$frameIndex", ATTEMPT_ID, frameIndex, time, coordinates)
    }

    private fun withConfidence(frame: PoseFrame, visibility: Float, presence: Float): PoseFrame {
        val coordinates = frame.coordinates.copyOf()
        for (index in 0 until PoseFrame.LANDMARK_COUNT) {
            val offset = index * PoseFrame.VALUES_PER_LANDMARK
            coordinates[offset + VISIBILITY_OFFSET] = visibility
            coordinates[offset + PRESENCE_OFFSET] = presence
        }
        return frame.copy(coordinates = coordinates)
    }

    private companion object {
        const val ATTEMPT_ID = "attempt-hesitation-test"
        const val FIRST_FRAME_INDEX = 10
        const val SQUARE_ASPECT_RATIO = 1f
        const val TORSO_LENGTH = 0.2f
        val SAMPLE_TIMES = listOf(0L, 250L, 500L, 750L, 1_000L)

        // MediaPipe landmark IDs and offsets in the existing PoseFrame coordinate layout.
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
        const val VISIBILITY_OFFSET = 3
        const val PRESENCE_OFFSET = 4
    }
}
