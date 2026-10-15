@file:Suppress("MagicNumber") // Quarter-turn camera coordinates and center-of-cell sampling.

package com.example.okulo.camera

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val HIGHLIGHT_GRID_EDGE = 160

// Warn at nominal YUV white as well as full-range highlights.
private const val HIGHLIGHT_LUMA_THRESHOLD = 235
private const val UNSIGNED_LUMA_MASK = 0xff

internal data class HighlightRegions(val width: Int, val height: Int, val regions: List<Rect>)

/** Sample the visible Y plane into upright runs, independent of the preview's pixel dimensions. */
internal fun readCameraHighlights(image: ImageProxy): HighlightRegions? {
    val crop = image.cropRect
    if (crop.isEmpty) return null
    val luma = HighlightLuma(image)
    val width = luma.width
    val height = luma.height
    val regions = mutableListOf<Rect>()
    for (y in 0 until height) {
        var start = -1
        for (x in 0..width) {
            val bright = x < width && luma.clipped(x, y)
            if (bright && start < 0) start = x
            if (!bright && start >= 0) {
                regions += Rect(start, y, x, y + 1)
                start = -1
            }
        }
    }
    return if (regions.isEmpty()) null else HighlightRegions(width, height, regions)
}

private class HighlightLuma(image: ImageProxy) {
    private val crop = image.cropRect
    private val rotation = image.imageInfo.rotationDegrees
    private val swapped = rotation == 90 || rotation == 270
    private val sourceWidth = if (swapped) crop.height() else crop.width()
    private val sourceHeight = if (swapped) crop.width() else crop.height()
    private val scale = min(1f, HIGHLIGHT_GRID_EDGE.toFloat() / max(sourceWidth, sourceHeight))
    val width = max(1, (sourceWidth * scale).roundToInt())
    val height = max(1, (sourceHeight * scale).roundToInt())
    private val plane = image.planes[0]
    private val buffer = plane.buffer
    private val base = buffer.position()
    fun clipped(x: Int, y: Int): Boolean {
        val u = (x * 2 + 1) * sourceWidth / (width * 2)
        val v = (y * 2 + 1) * sourceHeight / (height * 2)
        val offset = when (rotation) {
            90 -> (crop.bottom - 1 - u) * plane.rowStride + (crop.left + v) * plane.pixelStride
            180 -> (crop.bottom - 1 - v) * plane.rowStride + (crop.right - 1 - u) * plane.pixelStride
            270 -> (crop.top + u) * plane.rowStride + (crop.right - 1 - v) * plane.pixelStride
            else -> (crop.top + v) * plane.rowStride + (crop.left + u) * plane.pixelStride
        }
        return (buffer.get(base + offset).toInt() and UNSIGNED_LUMA_MASK) >= HIGHLIGHT_LUMA_THRESHOLD
    }
}
