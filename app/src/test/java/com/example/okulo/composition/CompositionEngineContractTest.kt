package com.example.okulo.composition

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CancellationException
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CompositionEngineContractTest {
    @Test
    fun freeSearchCanSelectACropWithADifferentAspectFromTheSource() {
        val photo = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val model = RecordingModel { boxes ->
            boxes.map { box ->
                if (abs(box.left - 0.125f) < 1e-6f && box.top == 0f && box.bottom == 1f) 8f else 1f
            }.toFloatArray()
        }
        CompositionEngine(model).use { engine ->
            val result = engine.analyze(photo, AnalysisMode.Fast, null, { true }, {})
            assertEquals(1f, result.crop.width * 400 / (result.crop.height * 300), 1e-5f)
            assertEquals(8f, result.cropScore, 0f)
            assertTrue(result.candidateCount <= 400)
        }
        photo.recycle()
    }

    @Test
    fun targetAspectSearchSelectsTheHighestScoringEligibleCrop() {
        val photo = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val model = RecordingModel { boxes ->
            boxes.map { box ->
                when {
                    box == CropBox.FullFrame -> 100f
                    abs(box.top - 0.25f) < 1e-6f && box.left == 0f && box.right == 1f -> 7f
                    else -> 1f
                }
            }.toFloatArray()
        }
        CompositionEngine(model).use { engine ->
            val result = engine.analyze(photo, AnalysisMode.Fast, 4f / 3f, { true }, {})
            assertEquals(0f, result.crop.left, 1e-6f)
            assertEquals(0.25f, result.crop.top, 1e-6f)
            assertEquals(1f, result.crop.right, 1e-6f)
            assertEquals(1f, result.crop.bottom, 1e-6f)
            assertEquals(100f, result.originalScore, 0f)
            assertEquals(7f, result.cropScore, 0f)
            for (box in model.requests.single().drop(1)) {
                assertEquals(16f / 9f, box.width * 400 / (box.height * 300), 1e-5f)
            }
        }
        photo.recycle()
    }

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
