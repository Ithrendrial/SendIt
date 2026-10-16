package com.example.sendit.domain

// An analysis interval, using the original pose frame indices specified by the UML.
data class Finding(
    val id: String,
    val attemptId: String,
    val type: String,
    val startFrameIndex: Int,
    val endFrameIndex: Int
)