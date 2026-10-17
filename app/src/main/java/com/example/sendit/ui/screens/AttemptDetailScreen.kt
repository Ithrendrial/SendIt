package com.example.sendit.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.sendit.R
import com.example.sendit.domain.PoseFrame
import com.example.sendit.domain.Finding
import com.example.sendit.ui.theme.SendItSpacing

// The form details shown above the playback, taken from the saved route and attempt.
data class AttemptHeading(
    val routeName: String,
    val location: String,
    val grade: String,
    val outcome: String
)

// Shows the reconstructed climb and its playback controls.
@Composable
fun AttemptDetailScreen(
    poseFrames: List<PoseFrame>,
    videoAspectRatio: Float,
    heading: AttemptHeading,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    findings: List<Finding> = emptyList()
) {
    val playback = rememberSaveable(poseFrames, saver = PosePlaybackState.saver(poseFrames)) {
        PosePlaybackState(poseFrames)
    }
    // Finding boundaries are original frame indices; track placement uses their recorded times.
    val timestamps = remember(poseFrames) { poseFrames.associate { it.frameIndex to it.timestamp } }
    val trackIntervals = remember(timestamps, findings) {
        findings.filter { it.type == "HESITATION" || it.type == "CRUX" }.mapNotNull { finding ->
            val start = timestamps[finding.startFrameIndex] ?: return@mapNotNull null
            val end = timestamps[finding.endFrameIndex] ?: return@mapNotNull null
            if (end >= start) TrackInterval(start, end, finding.type == "CRUX") else null
        }.sortedBy { it.isCrux } // Draw crux last so it remains visible over hesitation.
    }

    val techniques = remember(timestamps, findings) {
        val names = mapOf(
            "FLAGGING" to "Possible flagging", "DYNO" to "Possible dyno", "HEEL_HOOK" to "Possible heel hook",
            "HIGH_STEP" to "High Step", "MANTLE" to "Mantle"
        )
        findings.mapNotNull { finding ->
            val name = names[finding.type] ?: return@mapNotNull null
            val time = timestamps[finding.startFrameIndex] ?: return@mapNotNull null
            TechniqueItem(name, time)
        }.sortedBy { it.startMs }
    }

    BoxWithConstraints(modifier.fillMaxSize().padding(SendItSpacing.screenPadding)) {
        // Reserve most of the viewport for the reconstruction; cards remain reachable by scrolling.
        val playerHeight = maxHeight * PlayerHeightFraction
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SendItSpacing.large)
        ) {
            AttemptHeader(heading, onBack)
            Card(modifier = Modifier.height(playerHeight).fillMaxWidth(), shape = MaterialTheme.shapes.large) {
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
                PlaybackControls(playback, trackIntervals)
            }
            if (poseFrames.isEmpty()) Text("No pose frames available")
            TechniqueCards(techniques, onSelect = playback::scrubTo)
        }
    }
}

private data class TechniqueItem(val name: String, val startMs: Long)

// Keeps the video usable while leaving a preview of the section below it.
private const val PlayerHeightFraction = 0.7f

@Composable
private fun TechniqueCards(techniques: List<TechniqueItem>, onSelect: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.padding(SendItSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(SendItSpacing.large)
        ) {
            Text("Techniques Used", style = MaterialTheme.typography.titleLarge)
            if (techniques.isEmpty()) {
                Text("No techniques identified", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (technique in techniques) {
                Card(
                    onClick = { onSelect(technique.startMs) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(SendItSpacing.extraSmall).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.secondary))
                        Row(
                            Modifier.weight(1f).padding(SendItSpacing.large),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SendItSpacing.small)
                        ) {
                            Text("Technique: ${technique.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    formatPlaybackTime(technique.startMs),
                                    Modifier.padding(horizontal = SendItSpacing.small, vertical = SendItSpacing.extraSmall),
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TrackInterval(val startMs: Long, val endMs: Long, val isCrux: Boolean)

private val ScrubThumbWidth = 8.dp
private val ScrubThumbHeight = 44.dp
// Compact vertical spacing and the gap between the track and time display.
private const val ControlsPaddingScale = 2f / 3f

// Back arrow on the left, with the route name and its details centred beside it.
@Composable
private fun AttemptHeader(heading: AttemptHeading, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BackButton(onBack, Modifier.align(Alignment.CenterStart))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(heading.routeName, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${heading.location} • ${heading.grade} • ${heading.outcome}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

// White back arrow, also used while the attempt is loading or failed to load.
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(
            painterResource(R.drawable.ic_arrow_back),
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Connects the scrub bar to the displayed pose and recorded time.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackControls(playback: PosePlaybackState, trackIntervals: List<TrackInterval>) {
    val hesitationColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cruxColor = MaterialTheme.colorScheme.primary
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(
                start = SendItSpacing.extraLarge,
                top = SendItSpacing.medium * ControlsPaddingScale,
                end = SendItSpacing.extraLarge,
                bottom = SendItSpacing.medium * ControlsPaddingScale
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Video scrub/progress bar
            Slider(
                value = playback.positionMs.toFloat(),
                onValueChange = { playback.scrubTo(it.toLong()) },
                valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat(),
                enabled = playback.durationMs > 0L,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Playback position" },
                // Orange pill thumb on a peach/dark position track, with no gap or end dot.
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
                        // Paint over the normal track so findings stay visible after the playhead passes.
                        modifier = Modifier.clip(CircleShape).drawWithContent {
                            drawContent()
                            for (interval in trackIntervals) {
                                val duration = playback.durationMs.coerceAtLeast(1L)
                                val startFraction = (interval.startMs.toFloat() / duration).coerceIn(0f, 1f)
                                val endFraction = (interval.endMs.toFloat() / duration).coerceIn(0f, 1f)
                                val left = if (isRtl) 1f - endFraction else startFraction
                                // A single usable frame still gets a visible point marker.
                                val markerWidth = if (interval.startMs == interval.endMs) {
                                    ScrubThumbWidth.toPx().coerceAtMost(size.width)
                                } else size.width * (endFraction - startFraction)
                                drawRect(
                                    color = if (interval.isCrux) cruxColor else hesitationColor,
                                    topLeft = Offset((size.width * left).coerceIn(0f, size.width - markerWidth), 0f),
                                    size = Size(markerWidth, size.height)
                                )
                            }
                        },
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SendItSpacing.small, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(SendItSpacing.small).background(Color.Black, CircleShape))
            Text("Moving", style = MaterialTheme.typography.labelSmall, color = hesitationColor)
            Box(Modifier.size(SendItSpacing.small).background(hesitationColor, CircleShape))
            Text("Hesitation", style = MaterialTheme.typography.labelSmall, color = hesitationColor)
            Box(Modifier.size(SendItSpacing.small).background(cruxColor, CircleShape))
            Text("Estimated crux", style = MaterialTheme.typography.labelSmall, color = cruxColor)
        }
    }
}

// Displays milliseconds as minutes and seconds, for ease of user reading.
private fun formatPlaybackTime(timeMs: Long): String {
    val seconds = timeMs / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
