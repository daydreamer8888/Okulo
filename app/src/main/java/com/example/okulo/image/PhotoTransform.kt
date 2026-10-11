package com.example.okulo.image

import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.okulo.composition.CropBox

internal enum class PhotoOperation { RotateClockwise, FlipHorizontal, FlipVertical }

/** A mirror followed by quarter turns, shared by preview and full resolution export. */
internal data class PhotoTransform(val turns: Int = 0, val mirrored: Boolean = false) {
    fun followedBy(operation: PhotoOperation): PhotoTransform = when (operation) {
        PhotoOperation.RotateClockwise -> copy(turns = (turns + 1) % FULL_TURN)
        PhotoOperation.FlipHorizontal -> copy(turns = (FULL_TURN - turns) % FULL_TURN, mirrored = !mirrored)
        PhotoOperation.FlipVertical -> copy(turns = (HALF_TURN - turns + FULL_TURN) % FULL_TURN, mirrored = !mirrored)
    }

    fun apply(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            if (mirrored) setScale(-1f, 1f)
            postRotate(turns * QUARTER_DEGREES)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }

    fun apply(box: CropBox): CropBox {
        var result = if (mirrored) CropBox(1f - box.right, box.top, 1f - box.left, box.bottom) else box
        repeat(turns) {
            result = CropBox(1f - result.bottom, result.left, 1f - result.top, result.right)
        }
        return result
    }
}

private const val FULL_TURN = 4
private const val HALF_TURN = 2
private const val QUARTER_DEGREES = 90f
