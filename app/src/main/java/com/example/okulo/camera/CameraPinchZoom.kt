package com.example.okulo.camera

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Observe two-finger zoom before the crop overlay, leaving single-finger editing intact. */
internal fun Modifier.cameraPinchZoom(zoom: CameraZoom, enabled: Boolean = true): Modifier =
    pointerInput(zoom, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val state = zoom.state ?: return@awaitEachGesture
            if (state.minZoomRatio >= state.maxZoomRatio) return@awaitEachGesture
            var ratio = state.zoomRatio
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) {
                    val scale = event.calculateZoom()
                    if (scale != 1f) {
                        ratio = (ratio * scale).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                        zoom.requestRatio(ratio)
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
