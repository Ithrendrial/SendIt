package com.example.sendit.domain

// A replaceable analysis algorithm. Each strategy returns intervals in the original pose sequence.
interface ClassificationStrategy {
    fun classify(frames: List<PoseFrame>, aspectRatio: Float): List<Finding>
}
