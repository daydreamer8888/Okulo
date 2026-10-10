package com.example.okulo.camera

import androidx.camera.core.CameraControl
import androidx.camera.core.ZoomState
import androidx.camera.view.CameraController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/** Adapts camera-reported zoom without substituting requested values for device state. */
internal class CameraZoom(
    private val setRatio: (Float) -> ListenableFuture<Void>,
    private val setLinear: (Float) -> ListenableFuture<Void>,
    private val executor: Executor,
    private val onChanged: () -> Unit,
    private val onFailure: () -> Unit = {}
) {
    var state by mutableStateOf<ZoomState?>(null)
        private set
    var changing by mutableStateOf(false)
        private set
    private var request = 0

    fun update(value: ZoomState?) {
        val previous = state
        val rangeChanged = previous?.minZoomRatio != value?.minZoomRatio ||
            previous?.maxZoomRatio != value?.maxZoomRatio
        if (previous != null && (previous.zoomRatio != value?.zoomRatio || rangeChanged)) {
            onChanged()
        }
        state = value
        if (value == null) {
            request++
            changing = false
        }
    }

    fun requestRatio(ratio: Float) {
        val current = state ?: return
        if (!ratio.isFinite()) return
        val value = ratio.coerceIn(current.minZoomRatio, current.maxZoomRatio)
        if (value != current.zoomRatio) apply { setRatio(value) }
    }

    fun requestLinear(linear: Float) {
        val current = state ?: return
        if (!linear.isFinite()) return
        val value = linear.coerceIn(0f, 1f)
        if (value != current.linearZoom) apply { setLinear(value) }
    }

    private fun apply(change: () -> ListenableFuture<Void>) {
        val id = ++request
        changing = true
        onChanged()
        try {
            val future = change()
            future.addListener({
                if (id == request) {
                    changing = false
                    try {
                        future.get()
                    } catch (failure: ExecutionException) {
                        if (failure.cause !is CameraControl.OperationCanceledException) onFailure()
                    } catch (_: java.util.concurrent.CancellationException) {
                        // A replacement request or camera shutdown cancels pending zoom work.
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        onFailure()
                    }
                }
            }, executor)
        } catch (_: IllegalStateException) {
            changing = false
            onFailure()
        } catch (_: IllegalArgumentException) {
            changing = false
            onFailure()
        }
    }
}

@Composable
internal fun ObserveCameraZoom(controller: CameraController, zoom: CameraZoom?) {
    if (zoom == null) return
    val owner = LocalLifecycleOwner.current
    DisposableEffect(controller, zoom, owner) {
        val observer = Observer<ZoomState> { zoom.update(it) }
        controller.zoomState.observe(owner, observer)
        onDispose {
            controller.zoomState.removeObserver(observer)
            zoom.update(null)
        }
    }
}
