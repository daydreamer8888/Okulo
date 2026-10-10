package com.example.okulo.camera

import android.graphics.Bitmap
import android.os.Looper
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.photo.TestAnalyzer
import com.example.okulo.photo.WorkGate
import com.example.okulo.photo.recommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraCompositionTest {
    @Test
    fun recommendsFromTheCurrentFrameUsingTheSelectedModeWithoutAnAdoptStep() {
        val frame = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        val analyzer = TestAnalyzer()
        val gate = WorkGate()
        analyzer.analyze = { bitmap, mode ->
            assertEquals(frame, bitmap)
            assertEquals(AnalysisMode.Standard, mode)
            gate.block()
            recommendation()
        }
        val composition = CameraComposition(analyzer)
        try {
            composition.recommend(frame, AnalysisMode.Standard)
            gate.awaitEntry()
            assertTrue(composition.state.value.busy)
            assertEquals(null, composition.state.value.crop)
            gate.release()
            await { !composition.state.value.busy }
            assertEquals(recommendation().crop, composition.state.value.crop)
            assertEquals(2f, composition.state.value.originalScore)
            assertEquals(3f, composition.state.value.cropScore)
            assertEquals(listOf<Float?>(null), analyzer.requestedRatios)
            assertFalse(composition.state.value.canRestore)
        } finally {
            gate.release()
            composition.close()
            assertTrue(analyzer.closed.await(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun cancellingAnActiveRequestRejectsItsLateResultAndAllowsAnotherRecommendation() {
        val first = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        val analyzer = TestAnalyzer()
        val gate = WorkGate()
        analyzer.analyze = { bitmap, _ ->
            if (bitmap == first) gate.block()
            recommendation().copy(cropScore = if (bitmap == first) 99f else 4f)
        }
        val composition = CameraComposition(analyzer)
        try {
            composition.recommend(first, AnalysisMode.Fast)
            gate.awaitEntry()
            composition.dismiss()
            assertFalse(composition.state.value.active)
            assertFalse(analyzer.isCurrent())
            assertFalse(first.isRecycled)
            composition.recommend(second, AnalysisMode.Fast)
            gate.release()
            await { composition.state.value.cropScore == 4f }
            assertEquals(2, analyzer.analysisCalls.get())
            assertTrue(first.isRecycled)
            composition.dismiss()
            assertEquals(null, composition.state.value.crop)
        } finally {
            gate.release()
            composition.close()
            assertTrue(analyzer.closed.await(10, TimeUnit.SECONDS))
            assertTrue(second.isRecycled)
        }
    }

    @Test
    fun resizingThenRestoringRejectsPendingScoresAndKeepsTheOriginalScoreVisible() {
        val source = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        val scoringFrame = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        val analyzer = TestAnalyzer()
        val gate = WorkGate()
        analyzer.evaluate = { crop ->
            gate.block()
            recommendation().copy(crop = crop, originalScore = 8f, cropScore = 9f)
        }
        val composition = CameraComposition(analyzer)
        try {
            composition.recommend(source, AnalysisMode.Fast)
            await { !composition.state.value.busy }
            val resized = recommendation().crop.copy(right = 0.7f)
            composition.updateCrop(resized)
            assertTrue(composition.state.value.canRestore)
            composition.evaluateCrop(scoringFrame, AnalysisMode.Fast)
            gate.awaitEntry()
            assertTrue(composition.state.value.scoring)
            assertFalse(composition.state.value.busy)
            assertEquals(2f, composition.state.value.originalScore)
            assertEquals(null, composition.state.value.cropScore)
            composition.restore()
            assertEquals(recommendation().crop, composition.state.value.crop)
            assertEquals(3f, composition.state.value.cropScore)
            assertFalse(composition.state.value.scoring)
            assertFalse(composition.state.value.canRestore)
            assertFalse(analyzer.isCurrent())
            gate.release()
        } finally {
            gate.release()
            composition.close()
            assertTrue(analyzer.closed.await(10, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(3f, composition.state.value.cropScore)
        }
    }

    @Test
    fun leavingTheSceneClearsTheExistingFrameAndRejectsAReplacementInFlight() {
        val analyzer = TestAnalyzer()
        val gate = WorkGate()
        val composition = CameraComposition(analyzer)
        try {
            composition.recommend(Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888), AnalysisMode.Fast)
            await { !composition.state.value.busy }
            analyzer.analyze = { _, _ ->
                gate.block()
                recommendation()
            }
            composition.recommend(Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888), AnalysisMode.Fast)
            gate.awaitEntry()
            composition.invalidateScene()
            assertFalse(composition.state.value.active)
            assertEquals(null, composition.state.value.originalScore)
            assertFalse(analyzer.isCurrent())
            gate.release()
        } finally {
            gate.release()
            composition.close()
            assertTrue(analyzer.closed.await(10, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(composition.state.value.active)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.nanoTime() < deadline) { "Camera composition timed out" }
            Thread.yield()
        }
    }
}
