package com.example.sendit.domain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sendit.data.AttemptEntity

// An analysis interval, using the original pose frame indices specified by the UML.
@Entity(
    tableName = "findings",
    foreignKeys = [ForeignKey(
        entity = AttemptEntity::class, parentColumns = ["id"], childColumns = ["attemptId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("attemptId")]
)
data class Finding(
    @PrimaryKey val id: String,
    val attemptId: String,
    val type: String,
    val startFrameIndex: Int,
    val endFrameIndex: Int
)
