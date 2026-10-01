package com.example.sendit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.sendit.domain.PoseFrame

private val SkeletonGreen = Color(0xFF42753B)

// MediaPipe landmark pairs, grouped as face, torso, arms/hands and legs/feet.
private val skeletonConnections = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
    11 to 12, 11 to 23, 12 to 24, 23 to 24,
    11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
    12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
    23 to 25, 25 to 27, 27 to 29, 29 to 31, 27 to 31,
    24 to 26, 26 to 28, 28 to 30, 30 to 32, 28 to 32
)

// Draws the recorded image coordinates as a body on a white background.
@Composable
internal fun PoseSkeleton(frame: PoseFrame?, modifier: Modifier = Modifier) {
    Canvas(
        modifier.background(Color.White).clipToBounds()
            .semantics { contentDescription = "Pose reconstruction" }
    ) {
        val coordinates = frame?.coordinates ?: return@Canvas
        if (coordinates.size != PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) return@Canvas

        // Normalized x/y values become pixels inside the video-shaped canvas.
        val points = (0 until PoseFrame.LANDMARK_COUNT).map { landmark ->
            val offset = landmark * PoseFrame.VALUES_PER_LANDMARK
            val x = coordinates[offset] * size.width
            val y = coordinates[offset + 1] * size.height
            if (x.isFinite() && y.isFinite()) Offset(x, y) else null
        }
        for ((start, end) in skeletonConnections) {
            val from = points[start]
            val to = points[end]
            if (from != null && to != null) {
                drawLine(SkeletonGreen, from, to, 2.dp.toPx(), StrokeCap.Round)
            }
        }
        for (point in points) {
            if (point != null) drawCircle(SkeletonGreen, 3.dp.toPx(), point)
        }
    }
}
