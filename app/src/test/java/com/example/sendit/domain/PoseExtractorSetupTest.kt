package com.example.sendit.domain

import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseOptionsTestAccess
import org.junit.Assert.assertEquals
import org.junit.Test

/** Configuration tests only - these do not load MediaPipe's Android native engine. */
class PoseExtractorSetupTest {
    // Checks MediaPipe is configured to process a video rather than a single image.
    @Test
    fun options_useVideoModeForImportedVideos() {
        val options = PoseExtractor.createOptions()

        assertEquals(RunningMode.VIDEO, PoseOptionsTestAccess.runningMode(options))
    }

    // Checks MediaPipe is set to look for one climber per frame.
    @Test
    fun options_requestOneClimberPerFrame() {
        val options = PoseExtractor.createOptions()

        assertEquals(1, PoseOptionsTestAccess.numPoses(options))
    }
}
