package com.example.okulo.photo

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CompositionAnalyzer
import com.example.okulo.composition.CompositionResult
import com.example.okulo.composition.CropBox
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class PhotoTestHarness : Closeable {
    val bitmap: Bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
    val analyzer = TestAnalyzer()
    var reader: (Uri) -> Bitmap = { bitmap }
    val model = PhotoViewModel(RuntimeEnvironment.getApplication(), analyzer) { _, uri -> reader(uri) }
    private val store = ViewModelStore().apply { put("photo", model) }
    private val gates = mutableListOf<WorkGate>()

    fun select(uri: Uri = Uri.parse("content://photos/one")) {
        model.selectPhoto(uri)
        await { !model.state.value.busy }
    }

    fun gate(): WorkGate = WorkGate().also { gates.add(it) }

    fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.nanoTime() < deadline) { "Photo state timed out: ${model.state.value}" }
            Thread.yield()
        }
    }

    override fun close() {
        gates.forEach { it.release() }
        store.clear()
        assertTrue("Analyzer was not released", analyzer.closed.await(10, TimeUnit.SECONDS))
        bitmap.recycle()
    }
}

internal class WorkGate {
    private val entered = CountDownLatch(1)
    private val released = CountDownLatch(1)

    fun block() {
        entered.countDown()
        check(released.await(10, TimeUnit.SECONDS)) { "Test did not release inference" }
    }

    fun awaitEntry() = assertTrue("Inference did not start", entered.await(10, TimeUnit.SECONDS))
    fun release() = released.countDown()
}

internal class TestAnalyzer : CompositionAnalyzer {
    val analysisCalls = AtomicInteger()
    val evaluationCalls = AtomicInteger()
    val closed = CountDownLatch(1)
    var isCurrent: () -> Boolean = { false }
        private set
    var analyze: (Bitmap, AnalysisMode) -> CompositionResult = { _, _ -> recommendation() }
    var evaluate: (CropBox) -> CompositionResult = { crop -> recommendation().copy(crop = crop, cropScore = 4f) }

    override fun analyze(
        bitmap: Bitmap,
        mode: AnalysisMode,
        isCurrent: () -> Boolean,
        status: (String) -> Unit
    ): CompositionResult {
        this.isCurrent = isCurrent
        analysisCalls.incrementAndGet()
        status("正在分析照片…")
        return analyze(bitmap, mode)
    }

    override fun evaluate(
        bitmap: Bitmap,
        mode: AnalysisMode,
        crop: CropBox,
        isCurrent: () -> Boolean
    ): CompositionResult {
        this.isCurrent = isCurrent
        evaluationCalls.incrementAndGet()
        return evaluate(crop)
    }

    override fun close() = closed.countDown()
}

internal fun recommendation(): CompositionResult =
    CompositionResult(CropBox(0.1f, 0.1f, 0.9f, 0.9f), 2f, 3f, 150, 1200, 250)
