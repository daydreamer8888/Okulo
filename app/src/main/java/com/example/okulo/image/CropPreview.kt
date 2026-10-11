package com.example.okulo.image

import android.graphics.Bitmap
import com.example.okulo.composition.CropBox
import kotlin.math.ceil
import kotlin.math.floor

internal fun cropPreview(bitmap: Bitmap, box: CropBox): Bitmap {
    val left = floor(box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top = floor(box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val right = ceil(box.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ceil(box.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}
