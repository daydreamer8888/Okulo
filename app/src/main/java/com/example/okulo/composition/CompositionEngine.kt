package com.example.okulo.composition

import android.graphics.Bitmap
import java.util.concurrent.CancellationException

internal class CompositionEngine(private val model: CropScoringModel) : CompositionAnalyzer {
    override fun analyze(
        bitmap: Bitmap,
        mode: AnalysisMode,
        normalizedRatio: Float?,
        isCurrent: () -> Boolean,
        status: (String) -> Unit
    ): CompositionResult {
        val loadStart = System.nanoTime()
        status("正在准备模型…")
        model.load(mode)
        checkCurrent(isCurrent)
        val loadMillis = elapsed(loadStart)
        val started = System.nanoTime()
        status("正在分析照片…")
        var candidates = normalizedRatio?.let(::cropCandidates) ?: freeCropCandidates(bitmap.width, bitmap.height)
        val coarseScores = score(bitmap, mode, candidates, isCurrent)
        val refinement = if (normalizedRatio == null) {
            refineFreeCrops(bitmap.width, bitmap.height, candidates, coarseScores)
        } else {
            emptyList()
        }
        val scores = if (refinement.isEmpty()) {
            coarseScores
        } else {
            val fineScores = score(bitmap, mode, refinement, isCurrent)
            candidates = candidates + refinement
            coarseScores + fineScores
        }
        val firstEligible = if (normalizedRatio == null) 0 else 1
        val best = (firstEligible until scores.size).maxBy { scores[it] }
        return CompositionResult(
            candidates[best],
            scores[0],
            scores[best],
            loadMillis,
            elapsed(started),
            candidates.size - 1
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
