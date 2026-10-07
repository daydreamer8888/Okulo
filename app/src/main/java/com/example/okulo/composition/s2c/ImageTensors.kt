@file:Suppress("MagicNumber") // RGB bit layout and published normalization constants.

package com.example.okulo.composition.s2c

import android.graphics.Bitmap
import kotlin.math.round

internal data class ImageTensors(
    val raw: FloatArray,
    val rawShape: LongArray,
    val normalized: FloatArray,
    val normalizedShape: LongArray
)

internal fun imageTensors(bitmap: Bitmap): ImageTensors {
    val scale = minOf(
        256.0 / minOf(bitmap.width, bitmap.height),
        2048.0 / maxOf(bitmap.width, bitmap.height)
    )
    val width = (round(bitmap.width * scale / 32) * 32).toInt().coerceAtLeast(32)
    val height = (round(bitmap.height * scale / 32) * 32).toInt().coerceAtLeast(32)
    val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
    return try {
        ImageTensors(
            pixels(bitmap, normalized = false),
            longArrayOf(1, 3, bitmap.height.toLong(), bitmap.width.toLong()),
            pixels(resized, normalized = true),
            longArrayOf(1, 3, height.toLong(), width.toLong())
        )
    } finally {
        if (resized !== bitmap) resized.recycle()
    }
}

private fun pixels(bitmap: Bitmap, normalized: Boolean): FloatArray {
    val count = bitmap.width * bitmap.height
    val packed = IntArray(count)
    bitmap.getPixels(packed, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val values = FloatArray(count * 3)
    val means = floatArrayOf(0.485f, 0.456f, 0.406f)
    val deviations = floatArrayOf(0.229f, 0.224f, 0.225f)
    for (channel in 0..2) {
        for (index in packed.indices) {
            val value = (packed[index] ushr (16 - channel * 8)) and 255
            values[channel * count + index] = if (normalized) {
                (value / 256f - means[channel]) / deviations[channel]
            } else {
                value / 255f
            }
        }
    }
    return values
}
