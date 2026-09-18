package com.example.sendit.ui

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sendit.ui.viewmodels.AttemptFormViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttemptFormViewModelTest {
    // Checks that a new form starts without a video selected, before the user chooses one.
    @Test
    fun newFormHasNoSelectedVideo() {
        assertNull(AttemptFormViewModel().selectedVideo)
    }

    // Checks that selecting a video makes its URI available from the ViewModel.
    @Test
    fun selectingVideoExposesItsUri() {
        val viewModel = AttemptFormViewModel()
        val video = Uri.parse("content://com.example.sendit.test/first.mp4")

        viewModel.selectVideo(video)

        assertEquals(video, viewModel.selectedVideo)
    }

    // Checks that choosing a second video replaces the first URI, so the form uses the latest selection.
    @Test
    fun selectingAnotherVideoReplacesPreviousSelection() {
        val viewModel = AttemptFormViewModel()
        val first = Uri.parse("content://com.example.sendit.test/first.mp4")
        val second = Uri.parse("content://com.example.sendit.test/second.mp4")

        viewModel.selectVideo(first)
        viewModel.selectVideo(second)

        assertEquals(second, viewModel.selectedVideo)
    }
}
