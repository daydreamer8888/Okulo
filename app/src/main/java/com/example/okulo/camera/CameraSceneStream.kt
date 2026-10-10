package com.example.okulo.camera

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val SCENE_SAMPLE_INTERVAL_MS = 250L
private const val UNSIGNED_BYTE_MASK = 0xff

/** Read sparse Y samples instead of copying full-resolution preview bitmaps. */
internal class CameraSceneStream(
    private val controller: CameraController,
    private val onScene: (IntArray) -> Unit
) : Closeable {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(true)
    private var lastSample = 0L

    fun start() {
        controller.setImageAnalysisAnalyzer(worker) { image ->
            try {
                val now = SystemClock.elapsedRealtime()
                if (active.get() && now - lastSample >= SCENE_SAMPLE_INTERVAL_MS && !image.cropRect.isEmpty) {
                    lastSample = now
                    val samples = sample(image)
                    main.post { if (active.get()) onScene(samples) }
                }
            } finally {
                image.close()
            }
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
