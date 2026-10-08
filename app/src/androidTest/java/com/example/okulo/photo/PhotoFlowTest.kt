package com.example.okulo.photo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.okulo.composition.CropAspect
import com.example.okulo.composition.CropBox
import com.example.okulo.composition.AnalysisMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhotoFlowTest {
    @Test
    fun photoImportAnalysisModeSwitchAndReplacementRejectStaleResults() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val store = ViewModelStore()
        lateinit var model: PhotoViewModel
        val source = File(application.cacheDir, "flow-scene.png")
        instrumentation.context.assets.open("scene.png").use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }
        val rotated = File(application.cacheDir, "flow-rotated.jpg")
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
        rotated.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(rotated.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        try {
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(application))[PhotoViewModel::class.java]
                model.selectPhoto(Uri.fromFile(source))
            }
            waitUntil { !model.state.value.busy }
            assertNotNull(model.state.value.photo)
            instrumentation.runOnMainSync {
                model.setMode(AnalysisMode.Fast)
                model.setAspect(CropAspect.Original)
                model.analyze()
            }
            waitUntil { !model.state.value.busy }
            val result = checkNotNull(model.state.value.result) { model.state.value.error ?: "No result" }
            assertEquals(250, result.candidateCount)
            assertTrue(result.crop.area >= 0.5f - 1e-6f)
            val manual = CropBox(0.2f, 0.2f, 0.6f, 0.6f)
            instrumentation.runOnMainSync { model.updateCrop(manual); model.evaluateCrop() }
            waitUntil { !model.state.value.busy }
            assertNotNull(model.state.value.manualScore)
            instrumentation.runOnMainSync { model.analyze() }
            waitUntil { !model.state.value.busy }
            assertNull(model.state.value.manualCrop)
            assertEquals(model.state.value.result?.crop, model.state.value.displayedCrop)
            instrumentation.runOnMainSync {
                model.updateCrop(CropBox(0.3f, 0.3f, 0.7f, 0.7f))
                assertNull(model.state.value.displayedScore)
                model.evaluateCrop()
                model.restoreRecommendation()
            }
            Thread.sleep(1000)
            assertNull(model.state.value.manualCrop)
            assertNull(model.state.value.manualScore)
            assertEquals(model.state.value.result?.crop, model.state.value.displayedCrop)
            instrumentation.runOnMainSync { model.analyze() }
            waitUntil { model.state.value.status == "正在分析照片…" }
            instrumentation.runOnMainSync { model.setMode(AnalysisMode.Standard) }
            Thread.sleep(2000)
            assertEquals(AnalysisMode.Standard, model.state.value.mode)
            assertNull(model.state.value.result)
            instrumentation.runOnMainSync { model.analyze() }
            waitUntil { !model.state.value.busy }
            assertNotNull(model.state.value.result)
            instrumentation.runOnMainSync { model.analyze() }
            waitUntil { model.state.value.status == "正在分析照片…" }
            instrumentation.runOnMainSync { model.selectPhoto(Uri.fromFile(rotated)) }
            waitUntil { !model.state.value.busy }
            assertEquals(407, model.state.value.photo?.width)
            assertEquals(500, model.state.value.photo?.height)
            assertNull(model.state.value.result)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            source.delete()
            rotated.delete()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 45_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for photo flow" }
            Thread.sleep(50)
        }
    }
}
