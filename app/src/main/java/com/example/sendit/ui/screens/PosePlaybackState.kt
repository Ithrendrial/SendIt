package com.example.sendit.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.example.sendit.domain.PoseFrame

// Keeps the timeline and displayed pose at the same playback position.
internal class PosePlaybackState(private val frames: List<PoseFrame>) {
    val durationMs = frames.lastOrNull()?.timestamp ?: 0L

    var positionMs by mutableLongStateOf(0L)
        private set

    val currentFrame: PoseFrame?
        // Finds the most recent sample without assuming evenly spaced frames.
        get() = frames.lastOrNull { it.timestamp <= positionMs }

    // Moves to the selected time, keeping it within the recorded sequence.
    fun scrubTo(timeMs: Long) {
        positionMs = timeMs.coerceIn(0L, durationMs)
    }

    companion object {
        // Keeps the selected position when the screen is recreated (for example on rotation).
        fun saver(frames: List<PoseFrame>) = Saver<PosePlaybackState, Long>(
            save = { it.positionMs },
            restore = { position -> PosePlaybackState(frames).also { it.scrubTo(position) } }
        )
    }
}
