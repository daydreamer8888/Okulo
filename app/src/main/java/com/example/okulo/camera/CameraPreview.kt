package com.example.okulo.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ExecutionException

@Composable
internal fun CameraPreview(
    capture: CameraCapture,
    onFrameSource: ((() -> Bitmap?)?) -> Unit = {},
    onScene: (IntArray) -> Unit = {},
    zoom: CameraZoom? = null,
    meteringEnabled: Boolean = true,
    showLevel: Boolean = false,
    showHighlights: Boolean = false
) {
    val context = LocalContext.current
    val controller = capture.controller
    val preview = remember(controller) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            contentDescription = "后置主摄实时预览"
            this.controller = controller
        }
    }
    val owner = LocalLifecycleOwner.current
    val status = remember(controller) { CameraPreviewStatus() }
    val sceneObserver by rememberUpdatedState(onScene)
    val highlightsEnabled by rememberUpdatedState(showHighlights)
    val scenes = remember(controller, owner) {
        CameraSceneStream(
            controller,
            highlightsEnabled = { highlightsEnabled },
            onHighlights = { status.highlights = it },
            onScene = { sceneObserver(it) }
        )
    }
    BindCameraPreview(capture, preview, scenes, status, onFrameSource)
    LaunchedEffect(showHighlights, status.stream) {
        if (!showHighlights || status.stream != PreviewView.StreamState.STREAMING) status.highlights = null
    }
    val metering = rememberCameraMetering(capture)
    ObserveCameraZoom(controller, zoom)
    ObserveCameraMetering(
        controller,
        metering,
        meteringEnabled && status.stream == PreviewView.StreamState.STREAMING
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
        CameraPreviewNotice(status.error, status.stream)
        if (showHighlights && status.stream == PreviewView.StreamState.STREAMING) {
            CameraHighlightOverlay(status.highlights)
        }
        CameraLevel(showLevel && status.stream == PreviewView.StreamState.STREAMING)
        CameraMeteringControls(metering.state, metering::requestExposure)
    }
}

private class CameraPreviewStatus {
    var stream by mutableStateOf(PreviewView.StreamState.IDLE)
    var error by mutableStateOf<String?>(null)
    var highlights by mutableStateOf<HighlightRegions?>(null)
}

@Composable
private fun BindCameraPreview(
    capture: CameraCapture,
    preview: PreviewView,
    scenes: CameraSceneStream,
    status: CameraPreviewStatus,
    onFrameSource: ((() -> Bitmap?)?) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = capture.controller
    DisposableEffect(controller, owner) {
        var active = true
        scenes.start()
        onFrameSource { if (status.stream == PreviewView.StreamState.STREAMING) preview.bitmap else null }
        val observer = Observer<PreviewView.StreamState> {
            status.stream = it
            capture.previewReady(it == PreviewView.StreamState.STREAMING)
        }
        preview.previewStreamState.observe(owner, observer)
        val initialization = controller.initializationFuture
        fun reportFailure(failure: Exception) {
            controller.unbind()
            preview.controller = null
            Log.e("OkuloCamera", "Camera initialization failed", failure)
            status.error = "相机启动失败，请重新进入拍摄页。"
        }
        initialization.addListener(
            {
                if (active) {
                    try {
                        initialization.get()
                        controller.bindToLifecycle(owner)
                    } catch (failure: ExecutionException) {
                        reportFailure(failure)
                    } catch (failure: IllegalStateException) {
                        reportFailure(failure)
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            active = false
            onFrameSource(null)
            capture.previewReady(false)
            preview.previewStreamState.removeObserver(observer)
            controller.unbind()
            scenes.close()
            preview.controller = null
        }
    }
}

@Composable
private fun CameraPreviewNotice(error: String?, stream: PreviewView.StreamState) {
    if (error != null || stream != PreviewView.StreamState.STREAMING) {
        Text(
            error ?: "正在启动相机…",
            Modifier.background(Color.Black.copy(alpha = 0.8f)).padding(12.dp),
            color = Color.White
        )
    }
}
