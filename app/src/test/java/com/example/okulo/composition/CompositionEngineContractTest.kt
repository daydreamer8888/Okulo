package com.example.okulo.composition

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CompositionEngineContractTest {
    @Test
    fun recommendationAndManualScoringUseTheInjectedModel() {
        val model = RecordingModel()
        val photo = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            CompositionEngine(model).use { engine ->
                val result = engine.analyze(photo, AnalysisMode.Fast, { true }, {})
                assertEquals(cropCandidates()[1], result.crop)
                assertEquals(10f, result.originalScore, 0f)
                assertEquals(5f, result.cropScore, 0f)
                assertEquals(250, result.candidateCount)
                val manual = engine.evaluate(photo, AnalysisMode.Standard, result.crop) { true }
                assertEquals(result.cropScore, manual.cropScore, 0f)
                assertEquals(listOf(CropBox.FullFrame, result.crop), model.requests.last())
                assertEquals(listOf(AnalysisMode.Fast, AnalysisMode.Standard), model.modes)
            }
            assertTrue(model.closed)
        } finally {
            photo.recycle()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsModelScoresWithWrongCount() = withPhoto { photo ->
        CompositionEngine(RecordingModel { floatArrayOf(1f) }).use { engine ->
            engine.analyze(photo, AnalysisMode.Fast, { true }, {})
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsNonFiniteModelScores() = withPhoto { photo ->
        CompositionEngine(RecordingModel { boxes -> FloatArray(boxes.size) { Float.NaN } }).use { engine ->
            engine.analyze(photo, AnalysisMode.Fast, { true }, {})
        }
    }

    @Test(expected = CancellationException::class)
    fun discardsScoresWhenRequestChangesDuringInference() = withPhoto { photo ->
        var current = true
        val model = RecordingModel { boxes ->
            current = false
            FloatArray(boxes.size)
        }
        CompositionEngine(model).use { engine ->
            engine.analyze(photo, AnalysisMode.Fast, { current }, {})
        }
    }

    private fun withPhoto(test: (Bitmap) -> Unit) {
        val photo = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            test(photo)
        } finally {
            photo.recycle()
        }
    }
}

private class RecordingModel(
    private val scoring: (List<CropBox>) -> FloatArray = { boxes ->
        FloatArray(boxes.size) { index -> if (index == 0) 10f else if (index == 1) 5f else 1f }
    }
) : CropScoringModel {
    val modes = mutableListOf<AnalysisMode>()
    val requests = mutableListOf<List<CropBox>>()
    var closed = false

    override fun load(mode: AnalysisMode) {
        modes.add(mode)
    }

    override fun score(
        bitmap: Bitmap,
        mode: AnalysisMode,
        candidates: List<CropBox>,
        isCurrent: () -> Boolean
    ): FloatArray {
        requests.add(candidates)
        return scoring(candidates)
    }

    override fun close() {
        closed = true
    }
}
