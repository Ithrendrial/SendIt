package com.example.sendit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.sendit.domain.PoseFrame
import com.example.sendit.ui.theme.SendItSpacing

// Shows the reconstructed climb and its playback controls.
@Composable
fun AttemptDetailScreen(
    poseFrames: List<PoseFrame>,
    videoAspectRatio: Float,
    modifier: Modifier = Modifier
) {
    val playback = remember(poseFrames) { PosePlaybackState(poseFrames) }
    PlaybackClock(playback)

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(SendItSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SendItSpacing.large)
    ) {
        Text("Attempt detail", style = MaterialTheme.typography.headlineSmall)
        Card(shape = MaterialTheme.shapes.large) {
            PoseSkeleton(
                frame = playback.currentFrame,
                modifier = Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
            )
            PlaybackControls(playback)
        }
        if (poseFrames.isEmpty()) Text("No pose frames available")
    }
}

// Uses elapsed screen time so playback speed does not depend on how many poses were detected.
@Composable
private fun PlaybackClock(playback: PosePlaybackState) {
    LaunchedEffect(playback, playback.isPlaying) {
        if (!playback.isPlaying) return@LaunchedEffect
        var previousTime = withFrameMillis { it }
        while (playback.isPlaying) {
            withFrameMillis { time ->
                playback.advanceBy(time - previousTime)
                previousTime = time
            }
        }
    }
}

// Connects the play button and scrub bar to the same playback state.
@Composable
private fun PlaybackControls(playback: PosePlaybackState) {
    Column(Modifier.padding(SendItSpacing.medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val action = if (playback.isPlaying) "Pause" else "Play"
            TextButton(
                onClick = playback::togglePlayback,
                enabled = playback.durationMs > 0L,
                modifier = Modifier.semantics { contentDescription = action }
            ) { Text(action) }
            Slider(
                value = playback.positionMs.toFloat(),
                onValueChange = { playback.seekTo(it.toLong()) },
                valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat(),
                enabled = playback.durationMs > 0L,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Playback position" }
            )
        }
        Text(
            "${formatPlaybackTime(playback.positionMs)} / ${formatPlaybackTime(playback.durationMs)}",
            modifier = Modifier.align(Alignment.End),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// Displays milliseconds as minutes and seconds, for ease of user reading.
private fun formatPlaybackTime(timeMs: Long): String {
    val seconds = timeMs / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
