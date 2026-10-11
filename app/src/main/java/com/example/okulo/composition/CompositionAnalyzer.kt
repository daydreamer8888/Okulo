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
    ): CompositionResult = analyze(bitmap, mode, 1f, isCurrent, status)

    /** Normalized crop width/height, or null to search across multiple aspects. */
    fun analyze(
        bitmap: Bitmap,
        mode: AnalysisMode,
        normalizedRatio: Float?,
        isCurrent: () -> Boolean,
        status: (String) -> Unit
    ): CompositionResult = analyze(bitmap, mode, CropSearch(normalizedRatio, REFERENCE_MINIMUM_AREA), isCurrent, status)

    fun analyze(
        bitmap: Bitmap,
        mode: AnalysisMode,
        search: CropSearch,
        isCurrent: () -> Boolean,
        status: (String) -> Unit
    ): CompositionResult

    fun evaluate(bitmap: Bitmap, mode: AnalysisMode, crop: CropBox, isCurrent: () -> Boolean): CompositionResult
}

/** Search limits are measured against the full source image. */
data class CropSearch(val normalizedRatio: Float?, val minimumArea: Float)

private const val REFERENCE_MINIMUM_AREA = 0.5f
