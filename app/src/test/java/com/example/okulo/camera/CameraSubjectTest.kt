package com.example.okulo.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import com.example.okulo.composition.AnalysisStage
import com.example.okulo.composition.CompositionAnalyzer
import com.example.okulo.composition.CompositionResult
import com.example.okulo.composition.CropBox
import com.example.okulo.composition.CropSearch
import com.example.okulo.photo.TestAnalyzer
import com.example.okulo.photo.WorkGate
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
class CameraSubjectTest {
    @Test
    fun selectedSubjectsStayOnTheCapturedImageAndCancellingReselectionKeepsTheResult() {
        val engine = TestAnalyzer()
        var requested: CropBox? = null
        var analyzed: Bitmap? = null
        val analyzer = object : CompositionAnalyzer by engine {
            override fun analyze(
                bitmap: Bitmap,
                search: CropSearch,
                isCurrent: () -> Boolean,
                status: (AnalysisStage) -> Unit
            ): CompositionResult {
                requested = search.retainedRegion
                analyzed = bitmap
                return engine.analyze(bitmap, search, isCurrent, status)
            }
        }
        val composition = CameraComposition(analyzer)
        val still = photo()
        val actions = CameraFrameActions(composition, { image() }, frozenCapture = { it(still) })
        val subject = CropBox(0.2f, 0.3f, 0.6f, 0.7f)
        try {
            actions.selectSubject()
            assertTrue(composition.state.value.selectingSubject)
            assertEquals(still, composition.state.value.frozenPhoto)
            composition.commitSubject(subject)
            assertFalse(composition.state.value.selectingSubject)
            val selection = composition.state.value
            actions.selectSubject()
            assertTrue(composition.state.value.selectingSubject)
            composition.cancelSubjectSelection()
            assertEquals(selection, composition.state.value)
            actions.recommend()
            await { !composition.state.value.busy }
            assertEquals(subject, requested)
            assertEquals(Color.RED, checkNotNull(analyzed).getPixel(0, 0))
            val result = composition.state.value
            actions.selectSubject()
            assertFalse(composition.state.value.selectingSubject)
            assertEquals(result, composition.state.value)
            composition.invalidateScene()
            assertFalse(composition.state.value.active)
            await { still.image.isRecycled }
        } finally {
            composition.close()
            assertTrue(engine.closed.await(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun cancellingRecommendationKeepsTheSelectedSubjectAndItsFrozenImage() {
        val engine = TestAnalyzer()
        val gate = WorkGate()
        engine.analyze = {
            gate.block()
            recommendation()
        }
        val composition = CameraComposition(engine)
        val still = photo()
        val actions = CameraFrameActions(composition, { image() }, frozenCapture = { it(still) })
        val subject = CropBox(0.2f, 0.3f, 0.6f, 0.7f)
        try {
            actions.selectSubject()
            composition.commitSubject(subject)
            actions.recommend()
            gate.awaitEntry()
            composition.dismiss()
            assertEquals(subject, composition.state.value.retainedRegion)
            assertEquals(still, composition.state.value.frozenPhoto)
            assertFalse(composition.state.value.busy)
            assertEquals(null, composition.state.value.crop)
        } finally {
            gate.release()
            composition.close()
            assertTrue(engine.closed.await(10, TimeUnit.SECONDS))
        }
    }

    private fun image() = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)

    private fun photo(): FrozenCameraPhoto {
        val file = File.createTempFile("subject-test-", ".jpg", RuntimeEnvironment.getApplication().cacheDir)
        return FrozenCameraPhoto(image().apply { eraseColor(Color.RED) }, file)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.nanoTime() < deadline) { "Subject selection timed out" }
            Thread.yield()
        }
    }
}
