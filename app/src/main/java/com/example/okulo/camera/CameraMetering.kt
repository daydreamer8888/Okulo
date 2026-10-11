package com.example.okulo.camera

import androidx.camera.core.CameraControl
import androidx.camera.core.ExposureState
import androidx.camera.view.CameraController
import androidx.camera.view.TapToFocusInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.math.roundToInt

internal data class CameraMeteringState(
    val focus: TapToFocusInfo? = null,
    val range: IntRange? = null,
    val index: Int = 0,
    val revision: Int = 0
)

/** Uses the camera's focus result and exposure limits, rejecting obsolete control completions. */
internal class CameraMetering(
    private val exposureState: () -> ExposureState?,
    private val setExposure: (Int) -> ListenableFuture<Int>,
    private val executor: Executor,
    private val onFailure: () -> Unit
) {
    var state by mutableStateOf(CameraMeteringState())
        private set
    private var request = 0

    fun updateFocus(info: TapToFocusInfo) {
        if (info.tapPoint == null || info.focusState == CameraController.TAP_TO_FOCUS_NOT_STARTED) {
            reset()
            return
        }
        val exposure = exposureState()
        val limits = exposure?.exposureCompensationRange
        val range = if (exposure?.isExposureCompensationSupported == true && limits != null &&
            limits.upper > limits.lower
        ) {
            limits.lower..limits.upper
        } else {
            null
        }
        state = CameraMeteringState(info, range, exposure?.exposureCompensationIndex ?: 0, state.revision + 1)
    }

    fun requestExposure(value: Float) {
        val range = state.range?.takeIf { value.isFinite() } ?: return
        val index = value.roundToInt().coerceIn(range)
        if (index == state.index) return
        val id = ++request
        state = state.copy(index = index, revision = state.revision + 1)
        try {
            val future = setExposure(index)
            future.addListener({
                if (id == request) {
                    try {
                        state = state.copy(index = future.get().coerceIn(range))
                    } catch (failure: ExecutionException) {
                        if (failure.cause !is CameraControl.OperationCanceledException) fail()
                    } catch (_: java.util.concurrent.CancellationException) {
                        // Camera shutdown or a newer request supersedes this control operation.
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        fail()
                    }
                }
            }, executor)
        } catch (_: IllegalStateException) {
            fail()
        } catch (_: IllegalArgumentException) {
            fail()
        }
    }

    private fun fail() {
        state = state.copy(index = exposureState()?.exposureCompensationIndex ?: 0)
        onFailure()
    }

    fun reset() {
        request++
        state = CameraMeteringState(revision = state.revision + 1)
    }
}
