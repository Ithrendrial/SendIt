package com.example.sendit.domain

// The form values captured when the user submits a video.
// routeId is set when an existing route was chosen, and null when the form describes a new route.
data class AttemptDetails(
    val routeName: String,
    val grade: String,
    val location: String,
    val recordedAt: Long,
    val outcome: String,
    val notes: String,
    val routeId: String? = null
)
