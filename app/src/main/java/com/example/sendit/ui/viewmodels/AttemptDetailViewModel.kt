package com.example.sendit.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sendit.data.AttemptEntity
import com.example.sendit.data.ClimbRepository
import com.example.sendit.domain.PoseFrame
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AttemptDetailViewModel(repository: ClimbRepository, attemptId: String) : ViewModel() {
    var selectedAttempt by mutableStateOf<AttemptEntity?>(null)
        private set
    var poseFrames by mutableStateOf<List<PoseFrame>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        // Loads saved poses in playback order, rather than keeping the worker's in-memory result.
        viewModelScope.launch {
            repository.observeAttempt(attemptId)
                .catch {
                    errorMessage = "Could not load this attempt."
                    isLoading = false
                }
                .collect { saved ->
                    selectedAttempt = saved?.attempt
                    poseFrames = saved?.frames.orEmpty().sortedBy { it.frameIndex }
                    errorMessage = if (saved?.attempt?.videoAspectRatio == null) "This attempt has no saved reconstruction." else null
                    isLoading = false
                }
        }
    }
}
