package com.example.sendit.ui

import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sendit.data.RouteEntity
import com.example.sendit.ui.screens.AttemptFormScreen
import com.example.sendit.ui.theme.SendItTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Checks which fields are required, and that cancelling a submitted form hands it back unchanged.
@RunWith(AndroidJUnit4::class)
class AttemptFormSubmissionTest {
    @get:Rule
    val compose = createComposeRule()

    private val overhang = RouteEntity("route-1", "Orange Overhang", "V6", "Northern Rocks")
    private val video = Uri.parse("content://com.example.sendit.test/first.mp4")
    private var processing by mutableStateOf(false)
    private var cancelCount = 0

    // Checks a video alone is not enough: a route must be chosen too.
    @Test
    fun uploadNeedsARoute() {
        showForm(selectedVideo = video)

        compose.onNodeWithText("UPLOAD & ANALYSE").performScrollTo().assertIsNotEnabled()
    }

    // Checks a route alone is not enough: a video must be chosen too.
    @Test
    fun uploadNeedsAVideo() {
        showForm(selectedVideo = null)
        chooseRoute()

        compose.onNodeWithText("UPLOAD & ANALYSE").performScrollTo().assertIsNotEnabled()
    }

    // Checks the upload button is available once both required fields are filled.
    @Test
    fun uploadIsEnabledWithRouteAndVideo() {
        showForm(selectedVideo = video)
        chooseRoute()

        compose.onNodeWithText("UPLOAD & ANALYSE").performScrollTo().assertIsEnabled()
    }

    // Checks there is nothing to cancel until the form has been submitted.
    @Test
    fun cancelButtonOnlyAppearsWhileProcessing() {
        showForm(selectedVideo = video)
        compose.onNodeWithText("Cancel").assertDoesNotExist()

        processing = true

        compose.onNodeWithText("Cancel").performScrollTo().assertExists()
    }

    // Checks pressing cancel asks the app to stop processing.
    @Test
    fun cancelButtonRequestsCancellation() {
        processing = true
        showForm(selectedVideo = video)

        compose.onNodeWithText("Cancel").performScrollTo().performClick()

        assertEquals(1, cancelCount)
    }

    // Checks the form is locked while processing, and editable again afterwards with what the user entered.
    @Test
    fun cancellingReturnsToEditableFormWithValuesKept() {
        showForm(selectedVideo = video, onCancel = { processing = false })
        chooseRoute()
        compose.onNodeWithContentDescription("Notes").performScrollTo().performTextInput("Fell at the crux")

        processing = true
        compose.onNodeWithContentDescription("Notes").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Route selector").assertIsNotEnabled()

        compose.onNodeWithText("Cancel").performScrollTo().performClick()

        compose.onNodeWithContentDescription("Notes").assertIsEnabled().assertTextEquals("Fell at the crux")
        compose.onNodeWithContentDescription("Route selector").assertIsEnabled()
        compose.onNodeWithText("Orange Overhang").assertExists()
    }

    private fun chooseRoute() {
        compose.onNodeWithContentDescription("Route selector").performScrollTo().performClick()
        compose.onNodeWithText("Orange Overhang").performClick()
    }

    private fun showForm(selectedVideo: Uri?, onCancel: () -> Unit = { cancelCount++ }) {
        compose.setContent {
            SendItTheme {
                Surface {
                    AttemptFormScreen(
                        selectedVideo = selectedVideo,
                        onVideoSelected = {},
                        processing = processing,
                        existingRoutes = listOf(overhang),
                        onCancel = onCancel
                    )
                }
            }
        }
    }
}
