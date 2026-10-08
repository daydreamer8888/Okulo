package com.example.okulo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.okulo.camera.CameraCapture
import com.example.okulo.camera.CameraScreen
import com.example.okulo.photo.CropActions
import com.example.okulo.photo.PhotoScreen
import com.example.okulo.photo.PhotoViewModel
import com.example.okulo.settings.AppSettings
import com.example.okulo.settings.SettingsScreen
import com.example.okulo.ui.theme.OkuloTheme
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LaunchedEffect(viewModel) {
                viewModel.saveEvents.collect { Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show() }
            }
            val cameraCapture = remember { CameraCapture(applicationContext) }
            val settings = remember { AppSettings(applicationContext) }
            var page by rememberSaveable { mutableStateOf(AppPage.Camera) }
            var settingsOrigin by rememberSaveable { mutableStateOf(AppPage.Camera) }
            val openSettings = {
                settingsOrigin = page
                page = AppPage.Settings
            }
            val back = { page = if (page == AppPage.Settings) settingsOrigin else AppPage.Camera }
            BackHandler(enabled = page != AppPage.Camera, onBack = back)
            val darkTheme = page == AppPage.Camera || isSystemInDarkTheme()
            SideEffect {
                val bars = if (darkTheme) {
                    SystemBarStyle.dark(AndroidColor.BLACK)
                } else {
                    SystemBarStyle.light(AndroidColor.WHITE, AndroidColor.WHITE)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            OkuloTheme(darkTheme = darkTheme, dynamicColor = false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (page == AppPage.Camera) {
                            CameraScreen(
                                cameraCapture,
                                onImportPhoto = { page = AppPage.Photo },
                                onSettings = openSettings,
                                modifier = Modifier.weight(1f)
                            )
                        } else if (page == AppPage.Settings) {
                            SettingsScreen(settings.showModelScores, settings::setModelScores, back)
                        } else {
                            AnalysisPage(viewModel, back, openSettings, settings.showModelScores)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisPage(
    viewModel: PhotoViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    showScores: Boolean
) {
    val state by viewModel.state.collectAsState()
    val saving by viewModel.saving.collectAsState()
    PhotoScreen(
        state = state,
        onPhoto = viewModel::selectPhoto,
        onMode = viewModel::setMode,
        onAspect = viewModel::setAspect,
        onTransform = viewModel::transformPhoto,
        onSave = viewModel::saveCrop,
        saving = saving,
        onAnalyze = viewModel::analyze,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        cropActions = CropActions(viewModel::updateCrop, viewModel::evaluateCrop, viewModel::restoreRecommendation),
        onBack = onBack,
        onSettings = onSettings,
        showModelScores = showScores
    )
}

private enum class AppPage { Camera, Photo, Settings }
