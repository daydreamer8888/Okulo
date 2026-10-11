package com.example.okulo.composition

internal data class CropSearchResult(
    val crop: CropBox,
    val originalScore: Float,
    val cropScore: Float,
    val candidateCount: Int
)
