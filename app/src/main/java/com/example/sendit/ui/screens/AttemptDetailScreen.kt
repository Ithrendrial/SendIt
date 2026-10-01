package com.example.sendit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.sendit.R
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
        modifier.fillMaxSize().padding(SendItSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SendItSpacing.large)
    ) {
        Text("Attempt detail", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            // Controls take their required height first; the body fits in the space left above them.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                PoseSkeleton(
                    frame = playback.currentFrame,
                    modifier = Modifier.aspectRatio(videoAspectRatio)
                )
            }
            PlaybackControls(playback)
        }
        if (poseFrames.isEmpty()) Text("No pose frames available")
    }
}

private val ScrubThumbWidth = 8.dp
private val ScrubThumbHeight = 44.dp
private val PlayButtonPadding = SendItSpacing.medium // 24dp icon + padding = 48dp minimum touch target
// Keeps the controls tight so the scrub bar has more room.
private const val ControlsPaddingScale = 2f / 3f

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackControls(playback: PosePlaybackState) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(
                start = SendItSpacing.extraSmall * ControlsPaddingScale, // The play icon has its own padding.
                top = SendItSpacing.medium * ControlsPaddingScale,
                end = SendItSpacing.large * ControlsPaddingScale,
                bottom = SendItSpacing.medium * ControlsPaddingScale
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val action = if (playback.isPlaying) "Pause" else "Play"
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            // Play/pause button
            Icon(
                painterResource(if (playback.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = action,
                tint = if (isPressed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = playback.durationMs > 0L,
                        role = Role.Button,
                        onClick = playback::togglePlayback
                    )
                    .padding(PlayButtonPadding)
            )
            // Video scrub/progress bar
            Slider(
                value = playback.positionMs.toFloat(),
                onValueChange = { playback.seekTo(it.toLong()) },
                valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat(),
                enabled = playback.durationMs > 0L,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Playback position" },
                // Orange pill thumb on a peach (played) / dark (remaining) track, with no gap or end dot.
                thumb = {
                    Box(
                        Modifier.width(ScrubThumbWidth)
                            .height(ScrubThumbHeight)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.background
                        ),
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = 0.dp,
                        drawStopIndicator = null
                    )
                }
            )
            // Time display in minutes and seconds
            Text(
                "${formatPlaybackTime(playback.positionMs)} / ${formatPlaybackTime(playback.durationMs)}",
                modifier = Modifier.padding(start = SendItSpacing.medium * ControlsPaddingScale),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Displays milliseconds as minutes and seconds, for ease of user reading.
private fun formatPlaybackTime(timeMs: Long): String {
    val seconds = timeMs / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
