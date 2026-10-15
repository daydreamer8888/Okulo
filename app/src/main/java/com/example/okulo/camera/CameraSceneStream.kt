package com.example.okulo.camera

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val SCENE_SAMPLE_INTERVAL_MS = 250L
private const val HIGHLIGHT_SAMPLE_INTERVAL_MS = 100L
private const val UNSIGNED_BYTE_MASK = 0xff

/** Read sparse Y samples instead of copying full-resolution preview bitmaps. */
internal class CameraSceneStream(
    private val controller: CameraController,
    private val highlightsEnabled: () -> Boolean = { false },
    private val onHighlights: (HighlightRegions?) -> Unit = {},
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val onScene: (IntArray) -> Unit
) : Closeable, ImageAnalysis.Analyzer {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(true)
    private var lastSample = -SCENE_SAMPLE_INTERVAL_MS
    private var lastHighlights = -HIGHLIGHT_SAMPLE_INTERVAL_MS

    fun start() = controller.setImageAnalysisAnalyzer(worker, this)

    override fun analyze(image: ImageProxy) {
        try {
            if (!active.get() || image.cropRect.isEmpty) return
            val now = clock()
            val scene = if (now - lastSample >= SCENE_SAMPLE_INTERVAL_MS) {
                lastSample = now
                sample(image)
            } else {
                null
            }
            val highlightDue = highlightsEnabled() && now - lastHighlights >= HIGHLIGHT_SAMPLE_INTERVAL_MS
            val highlights = if (highlightDue) {
                lastHighlights = now
                readCameraHighlights(image)
            } else {
                null
            }
            if (scene != null || highlightDue) {
                main.post {
                    if (active.get()) {
                        scene?.let(onScene)
                        if (highlightDue && highlightsEnabled()) onHighlights(highlights)
                    }
                }
            }
        } finally {
            image.close()
        }
    }

    private fun sample(image: ImageProxy): IntArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val crop = image.cropRect
        return IntArray(SCENE_GRID_SIZE * SCENE_GRID_SIZE) { index ->
            val x = crop.left + (index % SCENE_GRID_SIZE * 2 + 1) * crop.width() / (SCENE_GRID_SIZE * 2)
            val y = crop.top + (index / SCENE_GRID_SIZE * 2 + 1) * crop.height() / (SCENE_GRID_SIZE * 2)
            val offset = buffer.position() + y * plane.rowStride + x * plane.pixelStride
            buffer.get(offset).toInt() and UNSIGNED_BYTE_MASK
        }
    }

    override fun close() {
        active.set(false)
        controller.clearImageAnalysisAnalyzer()
        worker.shutdown()
    }
}
