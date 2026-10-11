package com.example.okulo.composition

import android.graphics.Bitmap
import java.util.concurrent.CancellationException

internal class CompositionEngine(private val model: CropScoringModel) : CompositionAnalyzer {
    override fun analyze(
        bitmap: Bitmap,
        mode: AnalysisMode,
        search: CropSearch,
        isCurrent: () -> Boolean,
        status: (AnalysisStage) -> Unit
    ): CompositionResult {
        val loadStart = System.nanoTime()
        status(AnalysisStage.Loading)
        model.load(mode)
        checkCurrent(isCurrent)
        val loadMillis = elapsed(loadStart)
        val started = System.nanoTime()
        status(AnalysisStage.Searching)
        val result = searchCrops(
            bitmap.width,
            bitmap.height,
            search,
            { candidates -> score(bitmap, mode, candidates, isCurrent) },
            { status(AnalysisStage.Refining) }
        )
        return CompositionResult(
            result.crop,
            result.originalScore,
            result.cropScore,
            loadMillis,
            elapsed(started),
            result.candidateCount
        )
    }

    override fun evaluate(
        bitmap: Bitmap,
        mode: AnalysisMode,
        crop: CropBox,
        isCurrent: () -> Boolean
    ): CompositionResult {
        val started = System.nanoTime()
        model.load(mode)
        checkCurrent(isCurrent)
        val scores = score(bitmap, mode, listOf(CropBox.FullFrame, crop), isCurrent)
        return CompositionResult(crop, scores[0], scores[1], 0, elapsed(started), 1)
    }

    private fun score(
        bitmap: Bitmap,
        mode: AnalysisMode,
        candidates: List<CropBox>,
        isCurrent: () -> Boolean
    ): FloatArray {
        checkCurrent(isCurrent)
        val scores = model.score(bitmap, mode, candidates, isCurrent)
        checkCurrent(isCurrent)
        check(scores.size == candidates.size && scores.all { it.isFinite() }) { "模型评分异常，请重试" }
        return scores
    }

    override fun close() = model.close()
}

private fun checkCurrent(isCurrent: () -> Boolean) {
    if (!isCurrent()) throw CancellationException("分析已取消")
}

private const val NANOS_PER_MILLI = 1_000_000
private fun elapsed(start: Long): Long = (System.nanoTime() - start) / NANOS_PER_MILLI
