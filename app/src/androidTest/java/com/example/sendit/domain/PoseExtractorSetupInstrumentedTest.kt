package com.example.sendit.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the packaged model and native runtime together on an Android device. */
@RunWith(AndroidJUnit4::class)
class PoseExtractorSetupInstrumentedTest {
    @Test
    fun packagedModel_initializesPoseLandmarkerWithExtractorOptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = PoseExtractor.createOptions()

        val landmarker = PoseLandmarker.createFromOptions(context, options)
        try {
            assertNotNull(landmarker)
        } finally {
            landmarker.close()
        }
    }
}
