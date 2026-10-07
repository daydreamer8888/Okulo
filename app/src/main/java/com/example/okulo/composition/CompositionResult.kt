package com.example.okulo.composition

enum class AnalysisMode(val title: String, val detectorAsset: String) {
    Standard("标准分析", "detector-800.onnx"),
    Fast("快速分析", "detector-320.onnx")
}

data class CompositionResult(
    val crop: CropBox,
    val originalScore: Float,
    val cropScore: Float,
    val loadMillis: Long,
    val analysisMillis: Long,
    val candidateCount: Int
)
