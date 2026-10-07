package com.example.okulo.composition

import android.graphics.Bitmap
import java.io.Closeable

/** Scores follow candidate order, are finite, and increase with crop quality. */
internal interface CropScoringModel : Closeable {
    fun load(mode: AnalysisMode)
    fun score(
        bitmap: Bitmap,
        mode: AnalysisMode,
        candidates: List<CropBox>,
        isCurrent: () -> Boolean
    ): FloatArray
}
