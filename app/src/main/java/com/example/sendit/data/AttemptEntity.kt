package com.example.sendit.data

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.sendit.domain.PoseFrame

// Attempt table info
@Entity(
    tableName = "attempts",
    foreignKeys = [ForeignKey(
        entity = RouteEntity::class, parentColumns = ["id"], childColumns = ["routeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("routeId")]
)

// Info about climbing attempt the video belongs to
data class AttemptEntity(
    @PrimaryKey val id: String,
    val routeId: String,
    val recordedAt: Long,
    val outcome: String,
    val notes: String,
    val processingStatus: Boolean = true,
    val videoAspectRatio: Float? = null
)

// Loads the attempt, its poses, and its route together for playback and the page heading.
data class AttemptWithFrames(
    @Embedded val attempt: AttemptEntity,
    @Relation(parentColumn = "id", entityColumn = "attemptId") val frames: List<PoseFrame>,
    @Relation(parentColumn = "routeId", entityColumn = "id") val route: RouteEntity
)
