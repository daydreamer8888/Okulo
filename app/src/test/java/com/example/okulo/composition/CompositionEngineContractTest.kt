package com.example.okulo.composition

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun invalidSearchRequestsAreRejectedBeforeLoadingTheModel() = withPhoto { photo ->
        val model = RecordingModel()
        CompositionEngine(model).use { engine ->
            val invalid = listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)
            for (ratio in invalid) {
                assertThrows(IllegalArgumentException::class.java) {
                    engine.analyze(photo, AnalysisMode.Fast, CropSearch(ratio, 0.2f), { true }, {})
                }
            }
            for (area in invalid + 1.1f) {
                assertThrows(IllegalArgumentException::class.java) {
                    engine.analyze(photo, AnalysisMode.Fast, CropSearch(null, area), { true }, {})
                }
            }
            assertTrue(model.modes.isEmpty())
            assertTrue(model.requests.isEmpty())
        }
    }

    @Test
    fun configuredAreaFloorAppliesToOriginalFixedAndFreeSearchIncludingRefinement() {
        val photo = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        try {
            val searches = listOf(1f, 4f / 3f, null).flatMap { ratio ->
                listOf(0.2f, 0.7f).map { CropSearch(ratio, it) }
            }
            for (search in searches) assertAreaFloor(photo, search)
        } finally {
            photo.recycle()
        }
    }

    @Test
    fun freeSearchCanSelectACropWithADifferentAspectFromTheSource() {
        val photo = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val model = RecordingModel { boxes ->
            boxes.map { box ->
                if (abs(box.left - 0.125f) < 1e-6f && box.top == 0f && box.bottom == 1f) 8f else 1f
            }.toFloatArray()
        }
        CompositionEngine(model).use { engine ->
            val result = engine.analyze(photo, AnalysisMode.Fast, CropSearch(null, 0.5f), { true }, {})
            assertEquals(1f, result.crop.width * 400 / (result.crop.height * 300), 1e-5f)
            assertEquals(8f, result.cropScore, 0f)
            assertTrue(result.candidateCount <= 600)
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
            val result = engine.analyze(photo, AnalysisMode.Fast, CropSearch(4f / 3f, 0.5f), { true }, {})
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
                val result = engine.analyze(photo, AnalysisMode.Fast, CropSearch(1f, 0.5f), { true }, {})
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
            engine.analyze(photo, AnalysisMode.Fast, CropSearch(1f, 0.5f), { true }, {})
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsNonFiniteModelScores() = withPhoto { photo ->
        CompositionEngine(RecordingModel { boxes -> FloatArray(boxes.size) { Float.NaN } }).use { engine ->
            engine.analyze(photo, AnalysisMode.Fast, CropSearch(1f, 0.5f), { true }, {})
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
            engine.analyze(photo, AnalysisMode.Fast, CropSearch(1f, 0.5f), { current }, {})
        }
    }

    private fun assertAreaFloor(photo: Bitmap, search: CropSearch) {
        val ratio = search.normalizedRatio
        val minimum = search.minimumArea
        val model = RecordingModel { boxes -> boxes.map { -it.area }.toFloatArray() }
        CompositionEngine(model).use { engine ->
            val result = engine.analyze(photo, AnalysisMode.Fast, search, { true }, {})
            assertEquals(minimum, result.crop.area, 1e-5f)
            assertEquals(-1f, result.originalScore, 0f)
            assertEligibleCrops(model.requests.flatten(), ratio, minimum)
            assertTrue(result.candidateCount <= if (ratio == null) 600 else 250)
            if (ratio == null) assertEquals(2, model.requests.size)
        }
    }

    private fun assertEligibleCrops(boxes: List<CropBox>, ratio: Float?, minimum: Float) {
        for (box in boxes) {
            assertTrue("Crop $box is below $minimum", box.area >= minimum - 1e-6f)
            if (ratio != null && box != CropBox.FullFrame) {
                assertEquals(ratio, box.width / box.height, 1e-5f)
            }
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
