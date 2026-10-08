package com.example.okulo.composition.s2c

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CompositionAnalyzer
import com.example.okulo.composition.CropAspect
import com.example.okulo.composition.CropBox
import com.example.okulo.composition.createCompositionAnalyzer
import com.example.okulo.composition.cropCandidates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class S2cInferenceTest {
    @get:Rule val models = TemporaryFolder()
    private val context get() = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getNoBackupFilesDir() = models.root
    }

    @Test
    fun realModelScoresRequestedAndFreeAspectRecommendationsConsistently() {
        val photo = resource("scene.png").use { BitmapFactory.decodeStream(it) }
        try {
            createCompositionAnalyzer(context).use { analyzer ->
                for (aspect in listOf(CropAspect.Wide, CropAspect.Portrait, CropAspect.Free)) {
                    assertAspectRecommendation(analyzer, photo, aspect)
                }
            }
        } finally {
            photo.recycle()
        }
    }

    @Test
    fun bothDetectorSizesMatchIndependentReferenceScoresAndRanking() {
        val reference = resource("model-reference.json").bufferedReader().use { JSONObject(it.readText()) }
        val input = referenceInput()
        S2cCropScorer(context).use { model ->
            for (mode in AnalysisMode.entries) {
                model.load(mode)
                val actual = model.scoreInputs(input, cropCandidates())
                val expected = reference.getJSONObject("modes").getJSONObject(mode.name)
                assertReferenceScores(actual, expected)
            }
        }
    }

    @Test
    fun cachedManualScoreMatchesFreshInferenceAfterChangingModesAndPhotos() {
        val first = resource("scene.png").use { BitmapFactory.decodeStream(it) }
        val second = Bitmap.createScaledBitmap(first, 250, 204, true)
        try {
            createCompositionAnalyzer(context).use { analyzer ->
                for (mode in listOf(AnalysisMode.Fast, AnalysisMode.Fast, AnalysisMode.Standard, AnalysisMode.Fast)) {
                    compareManualAndFresh(analyzer, first, mode)
                }
                compareManualAndFresh(analyzer, second, AnalysisMode.Fast)
            }
        } finally {
            first.recycle()
            second.recycle()
        }
    }

    @Test
    fun preprocessingPreservesPublishedRgbNormalizationAndTensorLayout() {
        val bitmap = Bitmap.createBitmap(160, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff80ff00.toInt())
        try {
            val input = imageTensors(bitmap)
            assertTrue(input.rawShape.contentEquals(longArrayOf(1, 3, 128, 160)))
            assertTrue(input.normalizedShape.contentEquals(longArrayOf(1, 3, 256, 320)))
            assertEquals(128f / 255, input.raw[0], 1e-6f)
            assertEquals(1f, input.raw[160 * 128], 0f)
            assertEquals(0f, input.raw[2 * 160 * 128], 0f)
            assertEquals(0.06550218f, input.normalized[0], 1e-6f)
            assertEquals(2.411133f, input.normalized[320 * 256], 1e-5f)
            assertEquals(-1.8044444f, input.normalized[2 * 320 * 256], 1e-6f)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun veryTallPhotosUseBoundedStrideAlignedTensors() {
        val bitmap = Bitmap.createBitmap(64, 6000, Bitmap.Config.ARGB_8888)
        try {
            val input = imageTensors(bitmap)
            assertTrue(input.normalizedShape.contentEquals(longArrayOf(1, 3, 2048, 32)))
            assertEquals(3 * 2048 * 32, input.normalized.size)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun preprocessingKeepsTheCallerPhotoUsableWhenNoResizeIsNeeded() {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xffff0000.toInt())
        try {
            imageTensors(bitmap)
            assertEquals(0xffff0000.toInt(), bitmap.getPixel(0, 0))
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertAspectRecommendation(analyzer: CompositionAnalyzer, photo: Bitmap, aspect: CropAspect) {
        val ratio = aspect.normalizedRatio(photo.width, photo.height)
        val result = analyzer.analyze(photo, AnalysisMode.Fast, ratio, { true }, {})
        if (ratio != null) assertEquals(ratio, result.crop.width / result.crop.height, 1e-5f)
        assertTrue(result.cropScore.isFinite())
        val evaluated = analyzer.evaluate(photo, AnalysisMode.Fast, result.crop) { true }
        assertEquals(result.cropScore, evaluated.cropScore, 2e-5f)
        assertEquals(result.originalScore, evaluated.originalScore, 2e-5f)
    }

    private fun referenceInput(): ImageTensors {
        val raw = resource("scene.png").use { BitmapFactory.decodeStream(it) }
        val normalized = resource("scene-normalized.png").use { BitmapFactory.decodeStream(it) }
        return try {
            ImageTensors(
                fixturePixels(raw, false),
                longArrayOf(1, 3, raw.height.toLong(), raw.width.toLong()),
                fixturePixels(normalized, true),
                longArrayOf(1, 3, normalized.height.toLong(), normalized.width.toLong())
            )
        } finally {
            raw.recycle()
            normalized.recycle()
        }
    }

    private fun assertReferenceScores(actual: FloatArray, expected: JSONObject) {
        val scores = expected.getJSONArray("scores")
        assertEquals(scores.length(), actual.size)
        for (index in actual.indices) {
            assertEquals("crop $index", scores.getDouble(index), actual[index].toDouble(), 2e-5)
        }
        assertEquals(expected.getInt("top_index"), (1 until actual.size).maxBy { actual[it] })
    }

    private fun compareManualAndFresh(analyzer: CompositionAnalyzer, photo: Bitmap, mode: AnalysisMode) {
        val recommendation = analyzer.analyze(photo, mode, { true }, {})
        val manual = analyzer.evaluate(photo, mode, recommendation.crop) { true }
        assertEquals(recommendation.cropScore, manual.cropScore, 2e-5f)
        val adjusted = CropBox(0.2f, 0.2f, 0.6f, 0.6f)
        val cached = analyzer.evaluate(photo, mode, adjusted) { true }
        createCompositionAnalyzer(context).use { fresh ->
            val independent = fresh.evaluate(photo, mode, adjusted) { true }
            assertEquals(independent.originalScore, cached.originalScore, 2e-5f)
            assertEquals(independent.cropScore, cached.cropScore, 2e-5f)
            assertTrue(abs(cached.cropScore - manual.cropScore) > 1e-4f)
        }
    }

    private fun resource(name: String) = checkNotNull(javaClass.classLoader!!.getResourceAsStream(name))

    private fun fixturePixels(bitmap: Bitmap, normalized: Boolean): FloatArray {
        val count = bitmap.width * bitmap.height
        val packed = IntArray(count)
        bitmap.getPixels(packed, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val means = floatArrayOf(0.485f, 0.456f, 0.406f)
        val deviations = floatArrayOf(0.229f, 0.224f, 0.225f)
        return FloatArray(count * 3) { index ->
            val channel = index / count
            val value = (packed[index % count] ushr (16 - channel * 8)) and 255
            if (normalized) (value / 256f - means[channel]) / deviations[channel] else value / 255f
        }
    }
}
