package com.example.sendit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoUri: String,
    val createdAt: Long = System.currentTimeMillis()
)
