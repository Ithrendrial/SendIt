package com.example.sendit.domain

// The form values captured when the user submits a video.
data class AttemptDetails(
    val routeName: String,
    val grade: String,
    val location: String,
    val recordedAt: Long,
    val outcome: String,
    val notes: String
)
