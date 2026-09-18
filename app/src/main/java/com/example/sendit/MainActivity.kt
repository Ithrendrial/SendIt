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
import com.example.sendit.ui.screens.AttemptFormScreen
import com.example.sendit.ui.theme.SendItTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Keep the selected video when Android recreates the activity, such as after rotation.
        val attemptFormViewModel = ViewModelProvider(this)[AttemptFormViewModel::class.java]
        setContent {
            SendItTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AttemptFormScreen(
                        modifier = Modifier.padding(innerPadding),
                        selectedVideo = attemptFormViewModel.selectedVideo,
                        onVideoSelected = attemptFormViewModel::selectVideo
                    )
                }
            }
        }
    }
}
