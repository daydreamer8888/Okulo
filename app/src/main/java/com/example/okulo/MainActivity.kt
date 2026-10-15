package com.example.okulo

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.okulo.camera.CameraScreen
import com.example.okulo.camera.CameraSession
import com.example.okulo.camera.CameraSessionLifetime
import com.example.okulo.photo.PhotoScreen
import com.example.okulo.photo.PhotoViewModel
import com.example.okulo.settings.AppSettings
import com.example.okulo.settings.AppTheme
import com.example.okulo.settings.AppearanceSettings
import com.example.okulo.settings.SettingsScreen
import com.example.okulo.ui.PAGE_TRANSITION_MILLIS
import com.example.okulo.ui.PageTransition
import com.example.okulo.ui.crop.CropActions
import com.example.okulo.ui.theme.OkuloTheme
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OkuloApp(viewModel, onCameraPage = { camera ->
                requestedOrientation = if (camera) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }) { dark ->
                val bars = if (dark) {
                    SystemBarStyle.dark(AndroidColor.BLACK)
                } else {
                    SystemBarStyle.light(AndroidColor.WHITE, AndroidColor.WHITE)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
        }
    }
}

@Composable
private fun OkuloApp(viewModel: PhotoViewModel, onCameraPage: (Boolean) -> Unit, onTheme: (Boolean) -> Unit) {
    val context = LocalContext.current
    val camera = remember { CameraSession(context.applicationContext) }
    var choosingPhoto by rememberSaveable { mutableStateOf(false) }
    CameraSessionLifetime(camera, keepComposition = { choosingPhoto })
    val settings = remember { AppSettings(context.applicationContext) }
    LaunchedEffect(settings.cropWarningPercent, settings.freezeComposition) {
        viewModel.setMinimumCropArea(settings.minimumCropArea)
        camera.composition.invalidateScene()
    }
    var page by rememberSaveable { mutableStateOf(AppPage.Camera) }
    var settingsOrigin by rememberSaveable { mutableStateOf(AppPage.Camera) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        choosingPhoto = false
        if (uri != null) {
            camera.composition.invalidateScene()
            viewModel.selectPhoto(uri)
            page = AppPage.Photo
        }
    }
    val navigation = AppNavigation(
        page,
        back = { page = if (page == AppPage.Settings) settingsOrigin else AppPage.Camera },
        settings = {
            if (page != AppPage.Settings) {
                settingsOrigin = page
                page = AppPage.Settings
            }
        },
        importPhoto = {
            choosingPhoto = true
            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
    )
    val systemDark = isSystemInDarkTheme()
    val selectedDark = when (settings.theme) {
        AppTheme.System -> systemDark
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }
    val dark = page == AppPage.Camera || selectedDark
    SideEffect {
        onTheme(dark)
        onCameraPage(page == AppPage.Camera)
    }
    val background by animateColorAsState(
        if (dark) Color.Black else Color.White,
        tween(PAGE_TRANSITION_MILLIS),
        label = "page-background"
    )
    Box(Modifier.fillMaxSize().background(background)) {
        AppPages(viewModel, settings, camera, navigation, selectedDark)
    }
    BackHandler(enabled = page != AppPage.Camera, onBack = navigation.back)
}

@Composable
private fun AppPages(
    viewModel: PhotoViewModel,
    settings: AppSettings,
    camera: CameraSession,
    navigation: AppNavigation,
    selectedDark: Boolean
) {
    PageTransition(navigation.page, Modifier.fillMaxSize()) { visible ->
        OkuloTheme(
            darkTheme = visible == AppPage.Camera || selectedDark,
            dynamicColor = settings.dynamicColor && visible != AppPage.Camera
        ) {
            Scaffold(Modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    AppPageContent(viewModel, settings, camera, navigation.copy(page = visible))
                }
            }
        }
    }
}

@Composable
private fun AppPageContent(
    viewModel: PhotoViewModel,
    settings: AppSettings,
    camera: CameraSession,
    navigation: AppNavigation
) {
    when (navigation.page) {
        AppPage.Camera -> CameraScreen(
            camera.capture,
            camera.composition,
            showScores = settings.showModelScores,
            keepOriginal = settings.keepCaptureOriginal,
            minimumCropArea = settings.minimumCropArea,
            freezeComposition = settings.freezeComposition,
            onImportPhoto = navigation.importPhoto,
            onSettings = navigation.settings,
            modifier = Modifier.fillMaxSize()
        )
        AppPage.Settings -> SettingsScreen(
            settings.showModelScores,
            settings::setModelScores,
            navigation.back,
            keepOriginal = settings.keepCaptureOriginal,
            onKeepOriginal = settings::setCaptureOriginal,
            freezeComposition = settings.freezeComposition,
            onFreezeComposition = settings::updateFreezeComposition,
            showLevel = settings.showLevel,
            onShowLevel = settings::updateShowLevel,
            showHighlights = settings.showHighlights,
            onShowHighlights = settings::updateShowHighlights,
            cropWarningPercent = settings.cropWarningPercent,
            onCropWarning = settings::updateCropWarningPercent,
            appearance = {
                AppearanceSettings(
                    settings.theme,
                    settings::selectTheme,
                    settings.dynamicColor,
                    settings::updateDynamicColor
                )
            }
        )
        AppPage.Photo -> AnalysisPage(
            viewModel,
            navigation.back,
            navigation.settings,
            settings.showModelScores,
            settings.cropWarningPercent
        )
    }
}

private data class AppNavigation(
    val page: AppPage,
    val back: () -> Unit,
    val settings: () -> Unit,
    val importPhoto: () -> Unit
)

@Composable
private fun AnalysisPage(
    viewModel: PhotoViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    showScores: Boolean,
    cropWarningPercent: Int
) {
    val state by viewModel.state.collectAsState()
    val saving by viewModel.saving.collectAsState()
    PhotoScreen(
        state = state,
        onPhoto = viewModel::selectPhoto,
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
        showModelScores = showScores,
        saveEvents = viewModel.saveEvents,
        cropWarningPercent = cropWarningPercent
    )
}

private enum class AppPage { Camera, Photo, Settings }
