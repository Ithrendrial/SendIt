package com.example.sendit.domain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sendit.data.AttemptEntity

// A pose observation at [timestamp] milliseconds from the start of a video.
// Coordinates contain MediaPipe's 33 image landmarks in x/y/z/visibility/presence
// order. An empty array means no pose and NaN confidence means unavailable.
@Entity(
    tableName = "pose_frames",
    foreignKeys = [ForeignKey(
        entity = AttemptEntity::class, parentColumns = ["id"], childColumns = ["attemptId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["attemptId", "frameIndex"], unique = true)]
)
data class PoseFrame(
    @PrimaryKey val id: String,
    val attemptId: String,
    val frameIndex: Int,
    val timestamp: Long,
    val coordinates: FloatArray
) {
    companion object {
        const val LANDMARK_COUNT = 33
        const val VALUES_PER_LANDMARK = 5
    }
}
