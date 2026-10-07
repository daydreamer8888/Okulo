package com.example.okulo.photo

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.okulo.composition.CropBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

abstract class CropEditorBehavior {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dragAndCornerResizeUpdateCropAndFinishOncePerGesture() {
        val crop = mutableStateOf(CropBox(0.1f, 0.1f, 0.9f, 0.9f))
        val bitmap = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888)
        var finishes = 0
        compose.setContent { CropEditor(bitmap, crop.value, CropActions({ crop.value = it }, { finishes++ })) }
        compose.onNodeWithTag("crop-editor").performTouchInput {
            swipe(center, center + Offset(width * 0.05f, height * 0.05f))
        }
        compose.runOnIdle {
            assertTrue(crop.value.left > 0.12f)
            assertEquals(crop.value.width, crop.value.height, 1e-5f)
            assertEquals(1, finishes)
        }
        compose.onNodeWithTag("crop-editor").performTouchInput {
            swipe(
                Offset(width * crop.value.right, height * crop.value.bottom),
                Offset(width * 0.55f, height * 0.55f)
            )
        }
        compose.runOnIdle {
            assertTrue(crop.value.area < 0.5f)
            assertEquals(crop.value.width, crop.value.height, 1e-5f)
            assertEquals(2, finishes)
        }
    }

    @Test
    fun pinchResizesAndTouchOutsideCropDoesNotEdit() {
        val crop = mutableStateOf(CropBox(0.2f, 0.2f, 0.8f, 0.8f))
        var finishes = 0
        val bitmap = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888)
        compose.setContent { CropEditor(bitmap, crop.value, CropActions({ crop.value = it }, { finishes++ })) }
        compose.onNodeWithTag("crop-editor").performTouchInput {
            swipe(Offset(width * 0.02f, height * 0.02f), Offset(width * 0.02f, height * 0.1f))
        }
        compose.runOnIdle {
            assertEquals(CropBox(0.2f, 0.2f, 0.8f, 0.8f), crop.value)
            assertEquals(0, finishes)
        }
        compose.onNodeWithTag("crop-editor").performTouchInput {
            down(0, Offset(width * 0.4f, height * 0.5f))
            down(1, Offset(width * 0.6f, height * 0.5f))
            moveTo(0, Offset(width * 0.45f, height * 0.5f))
            moveTo(1, Offset(width * 0.55f, height * 0.5f))
            up(0)
            up(1)
        }
        compose.runOnIdle {
            assertTrue(crop.value.width < 0.6f)
            assertEquals(crop.value.width, crop.value.height, 1e-5f)
            assertEquals(1, finishes)
        }
    }
}
