package com.example.okulo.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CompositionAnalyzer
import com.example.okulo.composition.CompositionResult
import com.example.okulo.composition.CropBox
import com.example.okulo.photo.TestAnalyzer
import com.example.okulo.photo.recommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraFrozenCompositionTest {
    @Test
    fun recommendationsAndDragScoresStayOnTheFrozenPhotoUntilItIsClosed() {
        val engine = TestAnalyzer()
        engine.analyze = { image, _ -> recommendation().copy(originalScore = redScore(image)) }
        val analyzer = object : CompositionAnalyzer by engine {
            override fun evaluate(
                bitmap: Bitmap, mode: AnalysisMode, crop: CropBox, isCurrent: () -> Boolean
            ): CompositionResult = recommendation().copy(crop = crop, cropScore = redScore(bitmap))
        }
        val composition = CameraComposition(analyzer)
        var sceneColor = Color.RED
        val actions = CameraFrameActions(
            composition,
            { image(Color.BLUE) },
            { AnalysisMode.Fast },
            frozenCapture = { ready -> ready(photo(sceneColor)) }
        )
        try {
            actions.recommend()
            await { !composition.state.value.busy }
            assertEquals(2f, composition.state.value.originalScore)
            assertTrue(composition.state.value.frozenPhoto != null)
            val frozen = checkNotNull(composition.state.value.frozenPhoto)
            sceneColor = Color.BLUE
            composition.observeScene(IntArray(64) { if (it % 8 < 4) 20 else 200 })
            repeat(2) { composition.observeScene(IntArray(64) { if (it / 8 < 4) 20 else 200 }) }
            assertEquals(recommendation().crop, composition.state.value.crop)
            composition.updateCrop(CropBox(0f, 0f, 0.5f, 1f))
            actions.score()
            await { !composition.state.value.scoring }
            assertEquals(2f, composition.state.value.cropScore)
            actions.recommend()
            await { !composition.state.value.busy }
            assertEquals(2f, composition.state.value.originalScore)
            assertEquals(frozen, composition.state.value.frozenPhoto)
            composition.dismiss()
            assertFalse(composition.state.value.active)
            await { frozen.image.isRecycled }
            assertFalse(File(checkNotNull(frozen.uri.path)).exists())
        } finally {
            composition.close()
            assertTrue(engine.closed.await(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun cancellingBeforeCaptureCompletesRejectsAndReleasesTheLatePhoto() {
        val engine = TestAnalyzer()
        val composition = CameraComposition(engine)
        var ready: ((FrozenCameraPhoto?) -> Unit)? = null
        val actions = CameraFrameActions(
            composition, { image(Color.BLUE) }, { AnalysisMode.Fast },
            frozenCapture = { ready = it }
        )
        try {
            actions.recommend()
            assertTrue(composition.state.value.busy)
            composition.dismiss()
            val late = photo(Color.RED)
            checkNotNull(ready)(late)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(composition.state.value.active)
            assertTrue(late.image.isRecycled)
            assertFalse(File(checkNotNull(late.uri.path)).exists())
            assertEquals(0, engine.analysisCalls.get())
        } finally {
            composition.close()
            assertTrue(engine.closed.await(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun closingTheCameraSessionReleasesItsFrozenPhoto() {
        val engine = TestAnalyzer()
        val composition = CameraComposition(engine)
        val still = photo(Color.RED)
        CameraFrameActions(
            composition, { image(Color.BLUE) }, { AnalysisMode.Fast },
            frozenCapture = { it(still) }
        ).recommend()
        await { !composition.state.value.busy }
        composition.close()
        assertTrue(engine.closed.await(10, TimeUnit.SECONDS))
        assertTrue(still.image.isRecycled)
        assertFalse(File(checkNotNull(still.uri.path)).exists())
    }

    private fun image(color: Int) = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun photo(color: Int): FrozenCameraPhoto {
        val file = File.createTempFile("frozen-test-", ".jpg", RuntimeEnvironment.getApplication().cacheDir)
        return FrozenCameraPhoto(image(color), file)
    }

    private fun redScore(image: Bitmap) = if (Color.red(image.getPixel(0, 0)) > 200) 2f else 9f

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.nanoTime() < deadline) { "Frozen composition timed out" }
            Thread.yield()
        }
    }
}
