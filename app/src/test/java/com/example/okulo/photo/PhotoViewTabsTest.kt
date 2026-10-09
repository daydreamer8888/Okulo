package com.example.okulo.photo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.okulo.composition.CropAspect
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoViewTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun auxiliaryScoreCardKeepsPortraitSizeAcrossVisibilityAndLoadingChanges() {
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        val result = recommendation().copy(originalScore = 2.803f, cropScore = 3.126f)
        val state = mutableStateOf(PhotoState(photo = bitmap, result = result))
        val showScores = mutableStateOf(false)
        compose.setContent {
            OkuloTheme {
                Box(Modifier.size(360.dp, 500.dp)) {
                    PhotoScreen(state.value, {}, {}, {}, showModelScores = showScores.value)
                }
            }
        }
        val editor = compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot
        val tools = compose.onNodeWithContentDescription("保存").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("photo-scores")
        card.assertDoesNotExist()
        compose.runOnIdle { showScores.value = true }
        card.assertIsDisplayed()
        val cardBounds = card.fetchSemanticsNode().boundsInRoot
        compose.onNode(hasText("2.80") and hasAnyAncestor(hasTestTag("photo-scores"))).assertIsDisplayed()
        compose.onNodeWithText("3.13").assertIsDisplayed()
        assertTrue("Scores must remain a small lower-right surface", cardBounds.width < 200f)
        assertTrue(cardBounds.top > editor.bottom)
        assertTrue(cardBounds.right > editor.center.x)
        assertEquals(editor, compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = state.value.copy(manualCrop = state.value.result?.crop) }
        compose.onNodeWithContentDescription("正在更新裁剪评分").assertIsDisplayed()
        compose.onNodeWithText("~").assertDoesNotExist()
        compose.onNodeWithText("…").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(scoring = true) }
        compose.onNodeWithContentDescription("正在更新裁剪评分").assertIsDisplayed()
        assertEquals(cardBounds, card.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = state.value.copy(scoring = false, manualScore = 3.184f) }
        compose.onNodeWithText("3.18").assertIsDisplayed()
        compose.onNodeWithContentDescription("正在更新裁剪评分").assertDoesNotExist()
        assertEquals(cardBounds, card.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { showScores.value = false }
        card.assertDoesNotExist()
        assertEquals(editor, compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot)
        assertEquals(tools, compose.onNodeWithContentDescription("保存").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun previewKeepsTheEditedCropAndToolsWithoutAcceptingCropGestures() {
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        val state = mutableStateOf(PhotoState(photo = bitmap, result = recommendation(), aspect = CropAspect.Free))
        var changes = 0
        compose.setContent {
            OkuloTheme {
                Box(Modifier.size(360.dp, 500.dp)) {
                    PhotoScreen(
                        state.value,
                        {},
                        {},
                        {},
                        cropActions = CropActions(change = {
                            changes++
                            state.value = state.value.copy(manualCrop = it)
                        })
                    )
                }
            }
        }
        compose.onNodeWithText("编辑").assertIsSelected()
        val editor = compose.onNodeWithTag("crop-editor").assertIsDisplayed()
        val bounds = editor.fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithContentDescription("保存").fetchSemanticsNode().boundsInRoot
        assertTrue("Portrait editor must fit above the tools", bounds.bottom <= save.top)
        assertEquals(0.5f, bounds.width / bounds.height, 0.01f)
        editor.performTouchInput { swipe(center, center + Offset(width * 0.05f, 0f)) }
        assertTrue(changes > 0)
        val crop = state.value.displayedCrop
        val calls = changes
        val frame = compose.onNodeWithTag("photo-image-area").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("预览").performClick().assertIsSelected()
        editor.assertDoesNotExist()
        compose.onNodeWithContentDescription("裁剪预览").assertIsDisplayed().performTouchInput {
            swipe(center, center + Offset(0f, -height * 0.2f))
        }
        assertEquals(calls, changes)
        assertEquals(crop, state.value.displayedCrop)
        assertEquals(frame, compose.onNodeWithTag("photo-image-area").fetchSemanticsNode().boundsInRoot)
        assertEquals(save, compose.onNodeWithContentDescription("保存").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("编辑").performClick()
        editor.assertIsDisplayed()
        assertEquals(bounds, editor.fetchSemanticsNode().boundsInRoot)
        assertEquals(crop, state.value.displayedCrop)
    }
}
