package com.example.okulo.photo

import android.graphics.Bitmap
import android.util.Size
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CompositionResult
import com.example.okulo.composition.CropAspect
import com.example.okulo.composition.CropBox

internal data class PhotoState(
    val photo: Bitmap? = null,
    val mode: AnalysisMode = AnalysisMode.Fast,
    val result: CompositionResult? = null,
    val busy: Boolean = false,
    val scoring: Boolean = false,
    val status: String = "选择一张照片，看看裁剪建议",
    val error: String? = null,
    val manualCrop: CropBox? = null,
    val manualScore: Float? = null,
    val manualOriginalScore: Float? = null,
    val manualMillis: Long? = null,
    val aspect: CropAspect = CropAspect.Free,
    val freeRatio: Float? = null,
    val recommendationFreeRatio: Float? = null,
    val transform: PhotoTransform = PhotoTransform(),
    val sourceSize: Size? = null
) {
    val originalScore: Float? get() = result?.originalScore ?: manualOriginalScore
    val displayedCrop: CropBox? get() = manualCrop ?: result?.crop
    val displayedScore: Float? get() = if (manualCrop == null) result?.cropScore else manualScore
}
