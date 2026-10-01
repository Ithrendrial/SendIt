package com.example.sendit.ui

import com.example.sendit.domain.PoseFrame
import com.example.sendit.ui.screens.PosePlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PosePlaybackStateTest {
    private val frames = listOf(frame(0, 0), frame(1, 800), frame(2, 2000))

    // Checks scrubbing uses the recorded times, even when the frames are unevenly spaced.
    @Test
    fun seekingSelectsMostRecentFrameAtThatTime() {
        val playback = PosePlaybackState(frames)

        playback.seekTo(799)
        assertSame(frames[0], playback.currentFrame)
        playback.seekTo(800)
        assertSame(frames[1], playback.currentFrame)
        playback.seekTo(1900)
        assertSame(frames[1], playback.currentFrame)
        assertEquals(1900L, playback.positionMs)
    }

    // Checks play advances by elapsed time and pause keeps the body at the same position.
    @Test
    fun timeAdvancesOnlyWhilePlaying() {
        val playback = PosePlaybackState(frames)

        playback.togglePlayback()
        playback.advanceBy(900)
        assertTrue(playback.isPlaying)
        assertEquals(900L, playback.positionMs)
        assertSame(frames[1], playback.currentFrame)

        playback.togglePlayback()
        playback.advanceBy(500)
        assertFalse(playback.isPlaying)
        assertEquals(900L, playback.positionMs)
    }

    // Makes a pose with the same coordinate layout as the extractor, without running MediaPipe.
    private fun frame(index: Int, timestamp: Long) = PoseFrame(
        id = "frame-$index",
        attemptId = "attempt-1",
        frameIndex = index,
        timestamp = timestamp,
        coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK)
    )
}
