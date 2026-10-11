package com.example.okulo.photo

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.exifinterface.media.ExifInterface
import com.example.okulo.composition.CompositionResult
import com.example.okulo.composition.CropBox
import com.example.okulo.image.orientationMatrix
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyScreenAllowsPickingWithoutEditingActions() {
        compose.setContent {
            OkuloTheme { PhotoScreen(PhotoState(), {}, {}, {}) }
        }
        compose.onNodeWithText("选择照片").assertIsEnabled()
        compose.onNodeWithContentDescription("分析构图").assertDoesNotExist()
    }

    @Test
    fun cancelRemainsAvailableDuringAnalysis() {
        var cancelled = false
        compose.setContent {
            OkuloTheme {
                PhotoScreen(PhotoState(busy = true), {}, {}, { cancelled = true })
            }
        }
        compose.onNodeWithText("选择照片").assertIsEnabled()
        compose.onNodeWithContentDescription("取消").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun completedResultShowsOriginalAndPreviewWithoutRepeatingAnalysis() {
        val bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
        val result = CompositionResult(CropBox(0.1f, 0.1f, 0.9f, 0.9f), 2f, 3f, 0, 1200, 250)
        compose.setContent {
            OkuloTheme { PhotoScreen(PhotoState(photo = bitmap, result = result), {}, {}, {}) }
        }
        compose.onNodeWithContentDescription("可调整裁剪框的原图").assertIsDisplayed()
        compose.onNodeWithText("预览").performClick()
        compose.onNodeWithContentDescription("裁剪预览").assertIsDisplayed()
        compose.onNodeWithText("重新分析").assertDoesNotExist()
    }

    @Test
    fun exifRotationAndMirrorUseCorrectPixelCoordinates() {
        val colors = intArrayOf(0xff110000.toInt(), 0xff220000.toInt(), 0xff330000.toInt(),
            0xff440000.toInt(), 0xff550000.toInt(), 0xff660000.toInt())
        val bitmap = Bitmap.createBitmap(colors, 2, 3, Bitmap.Config.ARGB_8888)
        val cases = listOf(
            ExifInterface.ORIENTATION_ROTATE_90 to intArrayOf(4, 2, 0, 5, 3, 1),
            ExifInterface.ORIENTATION_TRANSPOSE to intArrayOf(0, 2, 4, 1, 3, 5),
            ExifInterface.ORIENTATION_TRANSVERSE to intArrayOf(5, 3, 1, 4, 2, 0)
        )
        for ((orientation, order) in cases) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, 2, 3, orientationMatrix(orientation), false)
            assertEquals(3, rotated.width)
            assertEquals(2, rotated.height)
            val pixels = IntArray(6)
            rotated.getPixels(pixels, 0, 3, 0, 0, 3, 2)
            assertArrayEquals(order.map { colors[it] }.toIntArray(), pixels)
            rotated.recycle()
        }
        bitmap.recycle()
    }
}
