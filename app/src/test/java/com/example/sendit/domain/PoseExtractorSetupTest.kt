package com.example.sendit.domain

import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseOptionsTestAccess
import org.junit.Assert.assertEquals
import org.junit.Test

/** Configuration tests only - these do not load MediaPipe's Android native engine. */
class PoseExtractorSetupTest {
    @Test
    fun options_useVideoModeForImportedVideos() {
        val options = PoseExtractor.createOptions()

        assertEquals(RunningMode.VIDEO, PoseOptionsTestAccess.runningMode(options))
    }

    @Test
    fun options_requestOneClimberPerFrame() {
        val options = PoseExtractor.createOptions()

        assertEquals(1, PoseOptionsTestAccess.numPoses(options))
    }
}
