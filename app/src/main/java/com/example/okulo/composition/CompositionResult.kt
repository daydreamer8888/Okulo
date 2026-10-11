package com.example.okulo.composition

data class CompositionResult(
    val crop: CropBox,
    val originalScore: Float,
    val cropScore: Float,
    val loadMillis: Long,
    val analysisMillis: Long,
    val candidateCount: Int
)
