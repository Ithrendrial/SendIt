package com.example.sendit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Info about route the attempt belongs to.
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val grade: String,
    val location: String,
    val tags: Set<String> = emptySet()
)
