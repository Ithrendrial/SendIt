package com.example.sendit.domain

import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/** Owns pose extraction setup; video decoding and extraction follow in the next increment. */
class PoseExtractor {
    companion object {
        private const val MODEL_ASSET_PATH = "pose_landmarker_lite.task"
        private const val CLIMBERS_PER_FRAME = 1

        // Building options is independent of Android's native inference runtime.
        internal fun createOptions(): PoseLandmarker.PoseLandmarkerOptions =
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET_PATH)
                        .setDelegate(Delegate.CPU)
                        .build()
                )
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(CLIMBERS_PER_FRAME)
                .build()
    }
}
