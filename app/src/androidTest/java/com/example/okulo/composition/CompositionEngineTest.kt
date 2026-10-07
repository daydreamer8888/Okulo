package com.example.okulo.composition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CompositionEngineTest {
    @Test
    fun bothModesMatchDesktopScoresAndBestCropOnIdenticalInputs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val reference = instrumentation.context.assets.open("model-reference.json").use {
            JSONObject(it.bufferedReader().readText())
        }
        val raw = instrumentation.context.assets.open("scene.png").use { BitmapFactory.decodeStream(it) }
        val normalized = instrumentation.context.assets.open("scene-normalized.png").use { BitmapFactory.decodeStream(it) }
        val input = ImageTensors(
            fixturePixels(raw, false), longArrayOf(1, 3, raw.height.toLong(), raw.width.toLong()),
            fixturePixels(normalized, true), longArrayOf(1, 3, normalized.height.toLong(), normalized.width.toLong())
        )
        CompositionEngine(instrumentation.targetContext).use { engine ->
            for (mode in AnalysisMode.entries) {
                engine.load(mode)
                val scores = engine.scoreInputs(input, cropCandidates())
                val expected = reference.getJSONObject("modes").getJSONObject(mode.name)
                val values = expected.getJSONArray("scores")
                assertEquals(values.length(), scores.size)
                for (index in scores.indices) {
                    assertTrue("${mode.name}: score $index", abs(scores[index] - values.getDouble(index)) < 2e-5)
                }
                assertEquals(expected.getInt("top_index"), (1 until scores.size).maxBy { scores[it] })
            }
        }
    }

    @Test
    fun manualScoringMatchesRecommendationWithCachedPhotoInBothModes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("scene.png").use { BitmapFactory.decodeStream(it) }
        CompositionEngine(instrumentation.targetContext).use { engine ->
            for (mode in AnalysisMode.entries) {
                val recommendation = engine.analyze(bitmap, mode, { true }, {})
                val manual = engine.evaluate(bitmap, mode, recommendation.crop) { true }
                assertEquals(recommendation.originalScore, manual.originalScore, 2e-5f)
                assertEquals(recommendation.cropScore, manual.cropScore, 2e-5f)
                assertEquals(recommendation.crop, manual.crop)
                val changed = engine.evaluate(bitmap, mode, CropBox(0.2f, 0.2f, 0.6f, 0.6f)) { true }
                assertTrue(changed.cropScore.isFinite())
                android.util.Log.i("OkuloValidation", "${mode.name} manual score: ${manual.analysisMillis} ms")
            }
        }
        bitmap.recycle()
    }

    @Test
    fun solidRgbPhotoHasExpectedChannelsAndNormalization() {
        val bitmap = Bitmap.createBitmap(160, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff80ff00.toInt())
        val input = imageTensors(bitmap)
        assertTrue(input.rawShape.contentEquals(longArrayOf(1, 3, 128, 160)))
        assertTrue(input.normalizedShape.contentEquals(longArrayOf(1, 3, 256, 320)))
        assertEquals(128f / 255, input.raw[0], 1e-6f)
        assertEquals(1f, input.raw[160 * 128], 0f)
        assertEquals(0f, input.raw[2 * 160 * 128], 0f)
        assertEquals((0.5f - 0.485f) / 0.229f, input.normalized[0], 1e-6f)
        assertEquals((255f / 256 - 0.456f) / 0.224f, input.normalized[320 * 256], 1e-6f)
        bitmap.recycle()
    }

    private fun fixturePixels(bitmap: Bitmap, normalized: Boolean): FloatArray {
        val means = floatArrayOf(0.485f, 0.456f, 0.406f)
        val deviations = floatArrayOf(0.229f, 0.224f, 0.225f)
        val count = bitmap.width * bitmap.height
        val packed = IntArray(count)
        bitmap.getPixels(packed, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return FloatArray(count * 3) { index ->
            val channel = index / count
            val value = (packed[index % count] ushr (16 - channel * 8)) and 255
            if (normalized) (value / 256f - means[channel]) / deviations[channel] else value / 255f
        }
    }
}
