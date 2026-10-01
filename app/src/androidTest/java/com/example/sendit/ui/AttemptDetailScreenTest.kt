package com.example.sendit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sendit.domain.PoseFrame
import com.example.sendit.ui.screens.AttemptDetailScreen
import com.example.sendit.ui.screens.AttemptHeading
import com.example.sendit.ui.theme.SendItTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttemptDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    // Checks the body is drawn at its recorded position on the display area.
    @Test
    fun drawsPoseOnWhiteBackgroundWithoutStretchingVideo() {
        showAttempt(listOf(frame(0), frame(2000)))

        val image = compose.onNodeWithContentDescription("Pose reconstruction").captureToImage()
        val pixels = image.toPixelMap()
        // A portrait source should stay portrait. The shoulder line is halfway across, a quarter down.
        assertEquals(0.75f, image.width.toFloat() / image.height, 0.01f)
        assertEquals(Color.White, pixels[image.width / 10, image.height / 10])
        assertNotEquals(Color.White, pixels[image.width / 2, image.height / 4])
    }

    // Checks moving the scrub bar updates the displayed time and clears a missing pose.
    @Test
    fun scrubbingUpdatesTimeAndDoesNotLeaveOldBodyOnScreen() {
        val missing = frame(800).copy(coordinates = floatArrayOf())
        showAttempt(listOf(frame(0), missing, frame(2000)))

        compose.onNodeWithContentDescription("Playback position")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1000f) }

        compose.onNodeWithText("0:01 / 0:02").assertIsDisplayed()
        val image = compose.onNodeWithContentDescription("Pose reconstruction").captureToImage()
        assertEquals(Color.White, image.toPixelMap()[image.width / 2, image.height / 4])
    }

    // Checks play moves the timeline and pause stops it, using the test clock instead of waiting.
    @Test
    fun playAndPauseControlTheTimeline() {
        compose.mainClock.autoAdvance = false
        showAttempt(listOf(frame(0), frame(2000)))
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithContentDescription("Play").performClick()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("0:01 / 0:02").assertIsDisplayed()

        compose.onNodeWithContentDescription("Pause").performClick()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("0:01 / 0:02").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    // Checks rotating the screen keeps the playback position instead of restarting from the beginning.
    @Test
    fun rotatingScreenKeepsPlaybackPosition() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            SendItTheme {
                AttemptDetailScreen(listOf(frame(0), frame(2000)), 0.75f, heading, onBack = {})
            }
        }
        compose.onNodeWithContentDescription("Playback position")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1000f) }
        compose.onNodeWithText("0:01 / 0:02").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("0:01 / 0:02").assertIsDisplayed()
    }

    // Checks pressing the back arrow asks to go back one page.
    @Test
    fun backArrowCallsOnBack() {
        var backPressed = false
        showAttempt(listOf(frame(0), frame(2000)), onBack = { backPressed = true })

        compose.onNodeWithContentDescription("Back").performClick()

        assertTrue(backPressed)
    }

    private val heading = AttemptHeading(
        routeName = "Orange Overhang",
        location = "Northern Rocks",
        grade = "V6",
        outcome = "Fall"
    )

    // Displays the page with supplied frames, without needing uploaded videos or a database.
    private fun showAttempt(frames: List<PoseFrame>, onBack: () -> Unit = {}) {
        compose.setContent {
            SendItTheme {
                AttemptDetailScreen(frames, 0.75f, heading, onBack)
            }
        }
    }

    // Places the shoulders apart so the image check can see their connecting line.
    private fun frame(timestamp: Long): PoseFrame {
        val coordinates = FloatArray(PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK)
        for (landmark in 0 until PoseFrame.LANDMARK_COUNT) {
            val offset = landmark * PoseFrame.VALUES_PER_LANDMARK
            coordinates[offset] = 0.5f
            coordinates[offset + 1] = 0.5f
            coordinates[offset + 3] = 1f
            coordinates[offset + 4] = 1f
        }
        // MediaPipe indices 11 and 12 are the left and right shoulders.
        coordinates[11 * PoseFrame.VALUES_PER_LANDMARK] = 0.25f
        coordinates[11 * PoseFrame.VALUES_PER_LANDMARK + 1] = 0.25f
        coordinates[12 * PoseFrame.VALUES_PER_LANDMARK] = 0.75f
        coordinates[12 * PoseFrame.VALUES_PER_LANDMARK + 1] = 0.25f
        return PoseFrame("frame-$timestamp", "attempt-1", 0, timestamp, coordinates)
    }
}
