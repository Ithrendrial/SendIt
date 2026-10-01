package com.example.sendit.ui.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.example.sendit.domain.AttemptDetails
import com.example.sendit.work.AttemptProcessingWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ViewModel that holds UI state for the attempt form. When the vaulues change, the UI is recomposed automatically.
class AttemptFormViewModel : ViewModel() {
    private var processingObserver: Job? = null
    private var cancelRequested = false

    var processingStatus by mutableStateOf(false)
        private set
    var analysisProgress by mutableStateOf<Int?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var completedAttemptId by mutableStateOf<String?>(null)
        private set
    // Selected video URI, or null if none. Only the ViewModel may modify this state.
    var selectedVideo: Uri? by mutableStateOf(null)
        private set

    // Set the currently selected video.
    fun selectVideo(video: Uri) {
        selectedVideo = video
        errorMessage = null
    }

    // Watches durable work so rotation or reopening the app does not lose a running job.
    fun observeProcessing(workManager: WorkManager) {
        if (processingObserver != null) return
        processingObserver = viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(AttemptProcessingWorker.WORK_NAME).collect { jobs ->
                val work = jobs.firstOrNull { !it.state.isFinished } ?: jobs.firstOrNull() ?: return@collect
                processingStatus = !work.state.isFinished
                analysisProgress = if (work.state == WorkInfo.State.RUNNING) {
                    work.progress.getInt(AttemptProcessingWorker.ANALYSIS_PROGRESS, -1).takeIf { it >= 0 }
                } else null
                when (work.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        selectedVideo = null
                        completedAttemptId = work.id.toString()
                    }
                    WorkInfo.State.FAILED -> errorMessage = "Video processing failed. Please select the video again."
                    WorkInfo.State.CANCELLED -> {
                        // Cancelling from the form is not an error; only unexpected cancellations are reported.
                        if (cancelRequested) cancelRequested = false
                        else errorMessage = "Processing was cancelled. Please try again."
                    }
                    else -> Unit
                }
            }
        }
    }

    // Retains document access before queuing work, so Android can process it after the screen closes.
    fun submitAttempt(context: Context, details: AttemptDetails) {
        val video = selectedVideo ?: return
        if (processingStatus) return
        val appContext = context.applicationContext
        processingStatus = true
        analysisProgress = null
        errorMessage = null
        completedAttemptId = null
        viewModelScope.launch {
            var permissionRetained = false
            try {
                val request = AttemptProcessingWorker.request(video, details)
                appContext.contentResolver.takePersistableUriPermission(video, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                permissionRetained = true
                WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(AttemptProcessingWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (permissionRetained) {
                    AttemptProcessingWorker.releaseVideoAccess(appContext, video)
                }
                processingStatus = false
                errorMessage = "Could not queue this video. Please select it again, or shorten the notes and retry."
            }
        }
    }

    // Stops the queued upload; the worker discards the unfinished attempt, and the form keeps its values.
    fun cancelProcessing(context: Context) {
        cancelRequested = true
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(AttemptProcessingWorker.WORK_NAME)
    }

    // Returns to the form without deleting the saved attempt.
    fun dismissResult() {
        completedAttemptId = null
    }
}
