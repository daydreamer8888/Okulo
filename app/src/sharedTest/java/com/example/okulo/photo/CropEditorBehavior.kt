package com.example.okulo.photo

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.okulo.composition.CropBox
import com.example.okulo.ui.crop.CropActions
import com.example.okulo.ui.crop.CropEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

abstract class CropEditorBehavior {
    @get:Rule val compose = createComposeRule()

    @Test
    fun accessibilityActionsMoveAndResizeTheCropAndFinishEachEdit() {
        val crop = mutableStateOf(CropBox(0.2f, 0.2f, 0.8f, 0.8f))
        val bitmap = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888)
        var finishes = 0
        compose.setContent {
            CropEditor(bitmap, crop.value, CropActions({ crop.value = it }, { finishes++ }), lockedRatio = null)
        }
        fun activate(label: String): Boolean {
            val actions = compose.onNodeWithTag("crop-editor").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
            var changed = false
            compose.runOnIdle { changed = actions.single { it.label == label }.action() }
            return changed
        }
        assertTrue(activate("向右移动"))
        assertEquals(0.25f, crop.value.left, 1e-5f)
        assertEquals(0.85f, crop.value.right, 1e-5f)
        assertTrue(activate("放大"))
        assertEquals(0.66f, crop.value.width, 1e-5f)
        assertEquals(0.66f, crop.value.height, 1e-5f)
        assertEquals(2, finishes)
        compose.runOnIdle { crop.value = CropBox.FullFrame }
        assertTrue(!activate("向右移动"))
        assertEquals(CropBox.FullFrame, crop.value)
        assertEquals(2, finishes)
    }

    @Test
    fun draggingEachEdgeResizesRatherThanMovesTheCrop() {
        val crop = mutableStateOf(CropBox(0.2f, 0.2f, 0.8f, 0.8f))
        val ratio = mutableStateOf<Float?>(null)
        val bitmap = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888)
        var finishes = 0
        compose.setContent {
            CropEditor(bitmap, crop.value, CropActions({ crop.value = it }, { finishes++ }), ratio.value)
        }
        val starts = listOf(Offset(0.2f, 0.5f), Offset(0.5f, 0.2f), Offset(0.8f, 0.5f), Offset(0.5f, 0.8f))
        val ends = listOf(Offset(0.3f, 0.5f), Offset(0.5f, 0.3f), Offset(0.7f, 0.5f), Offset(0.5f, 0.7f))
        for (locked in listOf(null, 1f)) {
            for (index in starts.indices) {
                compose.runOnIdle {
                    crop.value = CropBox(0.2f, 0.2f, 0.8f, 0.8f)
                    ratio.value = locked
                }
                compose.onNodeWithTag("crop-editor").performTouchInput {
                    swipe(
                        Offset(width * starts[index].x, height * starts[index].y),
                        Offset(width * ends[index].x, height * ends[index].y)
                    )
                }
                compose.runOnIdle {
                    val horizontal = index == 0 || index == 2
                    assertEquals(if (locked != null || horizontal) 0.5f else 0.6f, crop.value.width, 0.02f)
                    assertEquals(if (locked != null || !horizontal) 0.5f else 0.6f, crop.value.height, 0.02f)
                    when (index) {
                        0 -> assertEquals(0.8f, crop.value.right, 1e-5f)
                        1 -> assertEquals(0.8f, crop.value.bottom, 1e-5f)
                        2 -> assertEquals(0.2f, crop.value.left, 1e-5f)
                        else -> assertEquals(0.2f, crop.value.top, 1e-5f)
                    }
                }
            }
        }
        assertEquals(8, finishes)
    }

    @Test
    fun freeAspectCornerDragChangesWidthWithoutForcingHeight() {
        val crop = mutableStateOf(CropBox(0.2f, 0.2f, 0.8f, 0.8f))
        val bitmap = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888)
        compose.setContent {
            CropEditor(bitmap, crop.value, CropActions({ crop.value = it }), lockedRatio = null)
        }
        compose.onNodeWithTag("crop-editor").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.8f), Offset(width * 0.6f, height * 0.8f))
        }
        compose.runOnIdle {
            assertEquals(0.4f, crop.value.width, 0.02f)
            assertEquals(0.6f, crop.value.height, 0.02f)
            assertEquals(0.2f, crop.value.left, 1e-5f)
            assertEquals(0.2f, crop.value.top, 1e-5f)
        }
    }

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
