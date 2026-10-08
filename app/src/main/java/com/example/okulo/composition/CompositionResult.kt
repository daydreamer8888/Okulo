package com.example.okulo.composition

enum class AnalysisMode(val title: String) {
    Fast("快速分析"),
    Standard("标准分析")
}

data class CompositionResult(
    val crop: CropBox,
    val originalScore: Float,
    val cropScore: Float,
    val loadMillis: Long,
    val analysisMillis: Long,
    val candidateCount: Int
)
