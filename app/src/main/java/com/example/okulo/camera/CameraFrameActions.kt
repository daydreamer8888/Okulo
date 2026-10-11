package com.example.okulo.camera

import android.graphics.Bitmap
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.settings.DEFAULT_MINIMUM_CROP_AREA

internal class CameraFrameActions(
    private val composition: CameraComposition,
    private val source: () -> Bitmap?,
    private val mode: () -> AnalysisMode,
    private val minimumArea: () -> Float = { DEFAULT_MINIMUM_CROP_AREA }
) {
    fun recommend() {
        val frame = source()
        if (frame != null) composition.recommend(frame, mode(), minimumArea()) else composition.previewUnavailable()
    }

    fun score() {
        val frame = source()
        if (frame != null) composition.evaluateCrop(frame, mode()) else composition.previewUnavailable()
    }
}
