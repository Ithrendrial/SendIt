package com.example.sendit.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sendit.ui.screens.AttemptFormScreen
import com.example.sendit.ui.theme.SendItTheme
import com.example.sendit.ui.viewmodels.AttemptFormViewModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttemptVideoSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val viewModel = AttemptFormViewModel()
    private val picker = TestVideoPicker()

    // Checks that tapping the video area requests a video-only document picker.
    // A simulated successful result must update the ViewModel and show confirmation.
    @Test
    fun choosingVideoRequestsOnlyVideosAndDisplaysSelection() {
        val video = Uri.parse("content://com.example.sendit.test/first.mp4")
        picker.result = video
        showForm()

        chooseVideo()

        compose.runOnIdle {
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.launchedIntent?.action)
            assertArrayEquals(arrayOf("video/*"), picker.launchedIntent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
            assertEquals(video, viewModel.selectedVideo)
        }
        compose.onNodeWithText("Video selected").assertIsDisplayed()
        compose.onNodeWithText("Tap to choose a different video").assertIsDisplayed()
    }

    // Checks that cancelling the picker before choosing any video leaves the selection empty.
    @Test
    fun cancellingFirstSelectionLeavesFormEmpty() {
        showForm()

        chooseVideo()

        compose.runOnIdle { assertNull(viewModel.selectedVideo) }
        compose.onNodeWithText("Choose attempt video").assertIsDisplayed()
    }

    // Checks that opening the picker and cancelling does not discard a previously selected video.
    // Both the original URI and the screen's selection confirmation should remain.
    @Test
    fun cancellingReplacementKeepsPreviouslySelectedVideo() {
        val original = Uri.parse("content://com.example.sendit.test/first.mp4")
        viewModel.selectVideo(original)
        showForm()

        compose.onNodeWithText("Video selected").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(original, viewModel.selectedVideo) }
        compose.onNodeWithText("Video selected").assertIsDisplayed()
    }

    // Checks that choosing a replacement through the screen forwards the new URI to the ViewModel.
    @Test
    fun choosingReplacementUpdatesSelectedVideo() {
        val original = Uri.parse("content://com.example.sendit.test/first.mp4")
        val replacement = Uri.parse("content://com.example.sendit.test/second.mp4")
        viewModel.selectVideo(original)
        picker.result = replacement
        showForm()

        compose.onNodeWithText("Video selected").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(replacement, viewModel.selectedVideo) }
    }

    private fun chooseVideo() {
        compose.onNodeWithText("Choose attempt video").performScrollTo().performClick()
    }

    private fun showForm() {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = picker
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                SendItTheme {
                    Surface {
                        AttemptFormScreen(
                            selectedVideo = viewModel.selectedVideo,
                            onVideoSelected = viewModel::selectVideo
                        )
                    }
                }
            }
        }
    }

    // Fake video picker used by the tests.
    // Returns a preset video URI or cancellation without opening the real picker.
    private class TestVideoPicker : ActivityResultRegistry() {
        var result: Uri? = null
        var launchedIntent: Intent? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            launchedIntent = contract.createIntent(context, input)
            dispatchResult(
                requestCode,
                if (result == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                result?.let { Intent().setData(it) }
            )
        }
    }
}
