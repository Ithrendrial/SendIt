package com.google.mediapipe.tasks.vision.poselandmarker;

import com.google.mediapipe.tasks.vision.core.RunningMode;

/** Test-only access to the SDK's package-private configuration getters. */
public final class PoseOptionsTestAccess {
    // This helper only has static methods, so it does not need to be created.
    private PoseOptionsTestAccess() {}

    // Lets the Kotlin tests read the running mode, which the SDK only exposes within this package.
    public static RunningMode runningMode(PoseLandmarker.PoseLandmarkerOptions options) {
        return options.runningMode();
    }

    // Lets the tests check how many poses MediaPipe is configured to detect.
    public static int numPoses(PoseLandmarker.PoseLandmarkerOptions options) {
        return options.numPoses().get();
    }
}
