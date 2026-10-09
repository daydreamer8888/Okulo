package com.example.okulo.composition

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CancellationException
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FreeSearchTest {
    @Test
    fun cancellationAtRefinementProgressSkipsTheSecondInference() {
        val photo = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        var current = true
        var calls = 0
        var cancelled = false
        val model = object : CropScoringModel {
            override fun load(mode: AnalysisMode) = Unit
            override fun close() = Unit
            override fun score(
                bitmap: Bitmap,
                mode: AnalysisMode,
                candidates: List<CropBox>,
                isCurrent: () -> Boolean
            ): FloatArray {
                calls++
                return FloatArray(candidates.size) { 1f }
            }
        }
        try {
            CompositionEngine(model).use { engine ->
                try {
                    engine.analyze(photo, AnalysisMode.Fast, null, { current }) { status ->
                        if (status == "正在细化构图…") current = false
                    }
                } catch (_: CancellationException) {
                    cancelled = true
                }
                assertTrue(cancelled)
                assertFalse(current)
                assertEquals(1, calls)
            }
        } finally {
            photo.recycle()
        }
    }

    @Test
    fun refinementImprovesTheCoarseWinnerAndKeepsEveryAspectWithinSixHundredCrops() {
        val photo = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val requests = mutableListOf<List<CropBox>>()
        val model = object : CropScoringModel {
            override fun load(mode: AnalysisMode) = Unit
            override fun close() = Unit
            override fun score(
                bitmap: Bitmap,
                mode: AnalysisMode,
                candidates: List<CropBox>,
                isCurrent: () -> Boolean
            ): FloatArray {
                requests.add(candidates)
                return candidates.map(::quality).toFloatArray()
            }
        }
        try {
            CompositionEngine(model).use { engine ->
                val result = engine.analyze(photo, AnalysisMode.Fast, null, { true }, {})
                assertEquals(2, requests.size)
                val coarse = requests[0]
                val fine = requests[1]
                assertTrue(coarse.size <= 401)
                assertTrue(fine.size <= 200 && fine.isNotEmpty())
                assertTrue(result.candidateCount <= 600)
                assertEquals(coarse.size + fine.size - 1, result.candidateCount)
                assertEquals(coarse.size + fine.size, (coarse + fine).distinct().size)
                assertTrue(result.cropScore > coarse.maxOf(::quality))
                assertEquals(quality(CropBox.FullFrame), result.originalScore, 0f)
                val ratios = CropAspect.entries.mapNotNull { it.normalizedRatio(400, 300) }.distinct()
                for (ratio in ratios) {
                    assertTrue("No refinement for $ratio", fine.any { abs(it.width / it.height / ratio - 1f) < 1e-5f })
                }
                for (box in fine) {
                    assertTrue(box.left >= 0f && box.top >= 0f && box.right <= 1f && box.bottom <= 1f)
                    val maximum = minOf(box.width / box.height, box.height / box.width)
                    assertTrue(box.area >= maximum * 0.5f - 1e-6f)
                }
            }
        } finally {
            photo.recycle()
        }
    }
}

private fun quality(box: CropBox): Float = 100f - 100f *
    (abs(box.left - 0.19f) + abs(box.top - 0.13f) + abs(box.right - 0.79f) + abs(box.bottom - 0.93f))
