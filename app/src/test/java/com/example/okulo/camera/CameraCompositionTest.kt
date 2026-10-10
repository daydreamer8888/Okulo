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
