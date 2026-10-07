package com.example.okulo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.okulo.photo.CropActions
import com.example.okulo.photo.PhotoScreen
import com.example.okulo.photo.PhotoViewModel
import com.example.okulo.ui.theme.OkuloTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OkuloTheme {
                val state by viewModel.state.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    PhotoScreen(
                        state = state,
                        onPhoto = viewModel::selectPhoto,
                        onMode = viewModel::setMode,
                        onAnalyze = viewModel::analyze,
                        onCancel = viewModel::cancel,
                        onRetry = viewModel::retry,
                        cropActions = CropActions(
                            viewModel::updateCrop,
                            viewModel::evaluateCrop,
                            viewModel::restoreRecommendation
                        ),
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}
