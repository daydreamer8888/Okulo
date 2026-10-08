package com.example.okulo.photo

import android.graphics.Bitmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoTransformTest {
    @Test
    fun composedEditsHaveTheSamePixelOrientationAsTheVisibleOperations() {
        val colors = IntArray(6) { 0xff000000.toInt() or ((it + 1) shl 16) }
        val source = Bitmap.createBitmap(colors, 2, 3, Bitmap.Config.ARGB_8888)
        val cases = listOf(
            listOf(PhotoOperation.RotateClockwise) to intArrayOf(4, 2, 0, 5, 3, 1),
            listOf(PhotoOperation.FlipHorizontal) to intArrayOf(1, 0, 3, 2, 5, 4),
            listOf(PhotoOperation.FlipVertical) to intArrayOf(4, 5, 2, 3, 0, 1),
            listOf(PhotoOperation.RotateClockwise, PhotoOperation.FlipHorizontal) to intArrayOf(0, 2, 4, 1, 3, 5)
        )
        for ((operations, expected) in cases) {
            val transform = operations.fold(PhotoTransform()) { edit, operation -> edit.followedBy(operation) }
            val output = transform.apply(source)
            val pixels = IntArray(6)
            output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
            assertArrayEquals(expected.map { colors[it] }.toIntArray(), pixels)
            assertEquals(if (PhotoOperation.RotateClockwise in operations) 3 else 2, output.width)
        }
    }
}
