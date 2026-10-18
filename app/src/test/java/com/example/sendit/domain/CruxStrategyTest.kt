package com.example.sendit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CruxStrategyTest {
    // A longer exploratory hesitation wins over a shorter hesitation and a fully still rest.
    @Test
    fun classify_selectsOneCruxFromTheLongestHesitation() {
        val frames = (0L..13_500L step SAMPLE_INTERVAL_MS).mapIndexed { index, time ->
            val movingTime = (time - 4_000L).coerceAtLeast(0L) -
                (time - 6_000L).coerceIn(0L, 1_500L) - (time - 9_000L).coerceIn(0L, 3_000L)
            val exploring = time in 6_000L..7_500L || time in 9_000L..12_000L
            pose(index, time, movingTime / MILLIS_PER_SECOND * NORMAL_PROGRESS_SPEED, exploring)
        }

        val findings = CruxStrategy().classify(frames, ASPECT_RATIO)

        assertEquals(1, findings.size)
        val crux = findings.single()
        assertEquals("CRUX", crux.type)
        assertEquals(ATTEMPT_ID, crux.attemptId)
        assertTrue(timeAt(frames, crux.startFrameIndex) in 8_500L..9_500L)
        assertTrue(timeAt(frames, crux.endFrameIndex) in 11_500L..12_500L)
    }

    // There is still one estimated crux when all movement exceeds the hesitation speed threshold.
    @Test
    fun classify_selectsTheSlowestMovingWindowWithoutHesitation() {
        val frames = continuousClimb()
        assertTrue(HesitationStrategy().classify(frames, ASPECT_RATIO).isEmpty())

        val findings = CruxStrategy().classify(frames, ASPECT_RATIO)

        assertEquals(1, findings.size)
        assertEquals("CRUX", findings.single().type)
        assertEquals(FIRST_FRAME_INDEX + 8, findings.single().startFrameIndex) // 2 seconds.
        assertEquals(FIRST_FRAME_INDEX + 12, findings.single().endFrameIndex) // 3 seconds.
    }

    // Small high-frequency coordinate fluctuations should not move the crux to a faster section.
    @Test
    fun classify_keepsTheSameCruxWithSmallPoseJitter() {
        val clean = continuousClimb()
        val noisy = clean.mapIndexed { index, frame ->
            val coordinates = frame.coordinates.copyOf()
            val jitter = TORSO_LENGTH * if (index % 2 == 0) 0.02f else -0.02f
            for (landmark in 0 until PoseFrame.LANDMARK_COUNT) {
                coordinates[landmark * PoseFrame.VALUES_PER_LANDMARK + 1] += jitter
            }
            frame.copy(coordinates = coordinates)
        }

        val expected = CruxStrategy().classify(clean, ASPECT_RATIO).single()
        val actual = CruxStrategy().classify(noisy, ASPECT_RATIO).single()

        assertEquals(expected.startFrameIndex, actual.startFrameIndex)
        assertEquals(expected.endFrameIndex, actual.endFrameIndex)
    }

    // A still or very short but usable reconstruction also receives one fallback crux.
    @Test
    fun classify_returnsOneCruxWithoutAnyMovementEvidence() {
        val still = (0L..2_000L step SAMPLE_INTERVAL_MS).mapIndexed { index, time -> pose(index, time) }
        for (frames in listOf(still, listOf(still.first()))) {
            val findings = CruxStrategy().classify(frames, ASPECT_RATIO)

            assertEquals(1, findings.size)
            val crux = findings.single()
            assertEquals("CRUX", crux.type)
            assertEquals(ATTEMPT_ID, crux.attemptId)
            assertEquals(frames.first().frameIndex, crux.startFrameIndex)
            val expectedEnd = if (frames.size == 1) frames.first().frameIndex else FIRST_FRAME_INDEX + 4
            assertEquals(expectedEnd, crux.endFrameIndex)
        }
    }

    // The middle second progresses at 0.35 torso lengths/s; the rest progresses at 0.6.
    private fun continuousClimb() = (0L..5_000L step SAMPLE_INTERVAL_MS).mapIndexed { index, time ->
        val slowTime = (time - 2_000L).coerceIn(0L, 1_000L)
        val progress = (time - slowTime) / MILLIS_PER_SECOND * NORMAL_PROGRESS_SPEED +
            slowTime / MILLIS_PER_SECOND * SLOW_PROGRESS_SPEED
        pose(index, time, progress)
    }

    private fun timeAt(frames: List<PoseFrame>, index: Int) = frames.single { it.frameIndex == index }.timestamp

    // Reliable image landmarks with an upright torso and optional exploratory wrist movement.
    private fun pose(index: Int, time: Long, progress: Float = 0f, exploring: Boolean = false): PoseFrame {
        val coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) { Float.NaN }
        val hipY = 0.8f - progress * TORSO_LENGTH
        fun landmark(id: Int, x: Float, y: Float) {
            val offset = id * PoseFrame.VALUES_PER_LANDMARK
            floatArrayOf(x, y, 0f, 1f, 1f).copyInto(coordinates, offset)
        }
        landmark(11, 0.45f, hipY - TORSO_LENGTH)
        landmark(12, 0.55f, hipY - TORSO_LENGTH)
        landmark(23, 0.45f, hipY)
        landmark(24, 0.55f, hipY)
        val reach = if (exploring && index % 2 == 1) TORSO_LENGTH * 0.2f else 0f
        landmark(15, 0.35f + reach, hipY - TORSO_LENGTH / 2f)
        landmark(16, 0.65f, hipY - TORSO_LENGTH / 2f)
        landmark(27, 0.45f, hipY + TORSO_LENGTH)
        landmark(28, 0.55f, hipY + TORSO_LENGTH)
        val frameIndex = FIRST_FRAME_INDEX + index
        return PoseFrame("frame-$frameIndex", ATTEMPT_ID, frameIndex, time, coordinates)
    }

    private companion object {
        const val ATTEMPT_ID = "attempt-crux-test"
        const val FIRST_FRAME_INDEX = 10
        const val SAMPLE_INTERVAL_MS = 250L
        const val MILLIS_PER_SECOND = 1_000f
        const val ASPECT_RATIO = 1f
        const val TORSO_LENGTH = 0.2f
        const val NORMAL_PROGRESS_SPEED = 0.6f
        const val SLOW_PROGRESS_SPEED = 0.35f
    }
}
