package com.example.okulo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.okulo.camera.CameraCapture
import com.example.okulo.camera.CameraScreen
import com.example.okulo.photo.CropActions
import com.example.okulo.photo.PhotoScreen
import com.example.okulo.photo.PhotoViewModel
import com.example.okulo.ui.theme.OkuloTheme
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val cameraCapture = remember { CameraCapture(applicationContext) }
            var cameraPage by rememberSaveable { mutableStateOf(true) }
            SideEffect {
                val bars = if (cameraPage) {
                    SystemBarStyle.dark(AndroidColor.BLACK)
                } else {
                    SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            OkuloTheme(darkTheme = cameraPage || isSystemInDarkTheme(), dynamicColor = !cameraPage) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (cameraPage) Color.Black else MaterialTheme.colorScheme.background
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (cameraPage) {
                            CameraScreen(
                                cameraCapture,
                                onImportPhoto = { cameraPage = false },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            TextButton(onClick = { cameraPage = true }) { Text("返回拍摄") }
                            val state by viewModel.state.collectAsState()
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
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
