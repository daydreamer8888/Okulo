package com.example.okulo.camera

import android.graphics.Bitmap
import com.example.okulo.composition.AnalysisMode

internal class CameraFrameActions(
    private val composition: CameraComposition,
    private val source: () -> Bitmap?,
    private val mode: () -> AnalysisMode
) {
    fun recommend() {
        val frame = source()
        if (frame != null) composition.recommend(frame, mode()) else composition.previewUnavailable()
    }

    fun score() {
        val frame = source()
        if (frame != null) composition.evaluateCrop(frame, mode()) else composition.previewUnavailable()
    }
}
