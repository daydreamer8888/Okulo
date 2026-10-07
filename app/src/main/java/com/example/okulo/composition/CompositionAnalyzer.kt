package com.example.okulo.composition

import android.graphics.Bitmap
import java.io.Closeable

/** All calls, including close, belong to the same inference worker. */
interface CompositionAnalyzer : Closeable {
    fun analyze(
        bitmap: Bitmap,
        mode: AnalysisMode,
        isCurrent: () -> Boolean,
        status: (String) -> Unit
    ): CompositionResult

    fun evaluate(bitmap: Bitmap, mode: AnalysisMode, crop: CropBox, isCurrent: () -> Boolean): CompositionResult
}
