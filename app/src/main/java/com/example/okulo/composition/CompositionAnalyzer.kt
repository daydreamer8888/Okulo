package com.example.okulo.composition

import android.graphics.Bitmap
import java.io.Closeable

/** All calls, including close, belong to the same inference worker. */
interface CompositionAnalyzer : Closeable {
    fun analyze(
        bitmap: Bitmap,

        search: CropSearch,
        isCurrent: () -> Boolean,
        status: (AnalysisStage) -> Unit
    ): CompositionResult

    fun evaluate(bitmap: Bitmap, crop: CropBox, isCurrent: () -> Boolean): CompositionResult
}
