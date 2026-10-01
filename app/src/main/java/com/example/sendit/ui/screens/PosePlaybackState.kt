package com.example.sendit.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.sendit.domain.PoseFrame

// Keeps the timeline and displayed pose at the same playback position.
internal class PosePlaybackState(private val frames: List<PoseFrame>) {
    val durationMs = frames.lastOrNull()?.timestamp ?: 0L

    var positionMs by mutableLongStateOf(0L)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    val currentFrame: PoseFrame?
        // Finds the most recent sample without assuming evenly spaced frames.
        get() = frames.lastOrNull { it.timestamp <= positionMs }

    // Moves to the selected time, keeping it within the recorded sequence.
    fun seekTo(timeMs: Long) {
        positionMs = timeMs.coerceIn(0L, durationMs)
        if (positionMs == durationMs) isPlaying = false
    }

    // Starts or pauses playback, restarting if the sequence has already finished.
    fun togglePlayback() {
        if (durationMs == 0L) return
        if (positionMs == durationMs) positionMs = 0L
        isPlaying = !isPlaying
    }

    // Moves forward with the screen clock while playback is running.
    fun advanceBy(elapsedMs: Long) {
        if (isPlaying) seekTo(positionMs + elapsedMs.coerceAtLeast(0L))
    }
}
