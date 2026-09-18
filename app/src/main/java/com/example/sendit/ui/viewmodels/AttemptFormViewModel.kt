package com.example.sendit.ui.viewmodels

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// ViewModel that holds UI state for the attempt form.
class AttemptFormViewModel : ViewModel() {
    // Selected video URI, or null if none. Only the ViewModel may modify this state.
    var selectedVideo: Uri? by mutableStateOf(null)
        private set

    // Set the currently selected video.
    fun selectVideo(video: Uri) {
        selectedVideo = video
    }
}
