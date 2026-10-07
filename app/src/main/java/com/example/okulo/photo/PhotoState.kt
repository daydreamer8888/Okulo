package com.example.okulo.photo

import android.graphics.Bitmap
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CompositionResult

internal data class PhotoState(
    val photo: Bitmap? = null,
    val mode: AnalysisMode = AnalysisMode.Standard,
    val result: CompositionResult? = null,
    val busy: Boolean = false,
    val status: String = "选择一张照片，看看裁剪建议",
    val error: String? = null
)
