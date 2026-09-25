package com.google.mediapipe.tasks.vision.poselandmarker;

import com.google.mediapipe.tasks.vision.core.RunningMode;

/** Test-only access to the SDK's package-private configuration getters. */
public final class PoseOptionsTestAccess {
    private PoseOptionsTestAccess() {}

    public static RunningMode runningMode(PoseLandmarker.PoseLandmarkerOptions options) {
        return options.runningMode();
    }

    public static int numPoses(PoseLandmarker.PoseLandmarkerOptions options) {
        return options.numPoses().get();
    }
}
