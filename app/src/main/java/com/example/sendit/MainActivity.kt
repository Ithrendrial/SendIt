package com.example.sendit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.sendit.ui.viewmodels.AttemptFormViewModel
import androidx.work.WorkManager
import com.example.sendit.ui.theme.SendItTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sendit.data.ClimbRepository
import com.example.sendit.ui.screens.AttemptDetailScreen
import com.example.sendit.ui.screens.AttemptHeading
import com.example.sendit.ui.screens.BackButton
import com.example.sendit.ui.screens.AttemptFormScreen
import com.example.sendit.ui.viewmodels.AttemptDetailViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Keep the selected video when Android recreates the activity, such as after rotation.
        val attemptFormViewModel = ViewModelProvider(this)[AttemptFormViewModel::class.java]
        attemptFormViewModel.observeProcessing(WorkManager.getInstance(applicationContext))
        setContent {
            SendItTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SendItApp(
                        modifier = Modifier.padding(innerPadding),
                        form = attemptFormViewModel,
                        repository = (application as SendItApplication).repository
                    )
                }
            }
        }
    }
}

// Opens the saved result when processing succeeds, keeping video selection on its own page.
@Composable
private fun SendItApp(form: AttemptFormViewModel, repository: ClimbRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val attemptId = form.completedAttemptId
    if (attemptId == null) {
        AttemptFormScreen(
            modifier = modifier,
            selectedVideo = form.selectedVideo,
            onVideoSelected = form::selectVideo,
            processing = form.processingStatus,
            analysisProgress = form.analysisProgress,
            errorMessage = form.errorMessage,
            onSubmit = { form.submitAttempt(context, it) }
        )
    } else {
        val detail = viewModel<AttemptDetailViewModel>(key = attemptId, factory = viewModelFactory {
            initializer { AttemptDetailViewModel(repository, attemptId) }
        })
        BackHandler(onBack = form::dismissResult)
        Column(modifier) {
            when {
                detail.isLoading -> {
                    BackButton(form::dismissResult)
                    CircularProgressIndicator()
                }
                detail.errorMessage != null -> {
                    BackButton(form::dismissResult)
                    Text(detail.errorMessage!!)
                }
                else -> {
                    val attempt = detail.selectedAttempt!!
                    val route = detail.route!!
                    AttemptDetailScreen(
                        poseFrames = detail.poseFrames,
                        videoAspectRatio = attempt.videoAspectRatio!!,
                        heading = AttemptHeading(route.name, route.location, route.grade, attempt.outcome),
                        onBack = form::dismissResult
                    )
                }
            }
        }
    }
}
