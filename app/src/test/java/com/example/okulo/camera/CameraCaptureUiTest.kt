package com.example.okulo.camera

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class CameraCaptureUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun saveFeedbackUsesToolbarSpaceWithoutMovingOrCoveringCameraActions() {
        val state = mutableStateOf(CaptureUiState(ready = true, message = "已保存到相册"))
        compose.setContent { CameraLayout({}, capture = state.value) {} }
        compose.mainClock.autoAdvance = false
        val notice = compose.onNodeWithText("已保存到相册").fetchSemanticsNode().boundsInRoot
        val entry = compose.onNodeWithContentDescription("导入照片").fetchSemanticsNode().boundsInRoot
        val gear = compose.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
        assertEquals(entry.center.y, notice.center.y, 1f)
        assertTrue(notice.right <= entry.left)
        assertTrue(notice.right <= gear.left)
        assertTrue(notice.bottom < shutter.top)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { state.value = state.value.copy(message = null) }
        compose.onNodeWithText("已保存到相册").assertDoesNotExist()
        assertEquals(shutter, compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot)
        assertEquals(entry, compose.onNodeWithContentDescription("导入照片").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun saveConfirmationExpiresAndAppearsAgainForTheNextPhoto() {
        val state = mutableStateOf(
            CaptureUiState(ready = true, savedPhoto = Uri.parse("content://photos/1"), message = "已保存到相册")
        )
        compose.setContent { CameraLayout({}, capture = state.value) {} }
        compose.onNodeWithText("已保存到相册").assertExists()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("已保存到相册").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            state.value = state.value.copy(savedPhoto = Uri.parse("content://photos/2"))
        }
        compose.onNodeWithText("已保存到相册").assertExists()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("已保存到相册").assertDoesNotExist()
    }

    @Test
    fun shutterWaitsForTheCameraAndPreventsRepeatsWhileSavingButAllowsRetry() {
        val state = mutableStateOf(CaptureUiState())
        var requests = 0
        compose.setContent {
            CameraLayout(
                onImportPhoto = {},
                capture = state.value,
                onCapture = {
                    requests++
                    state.value = state.value.copy(saving = true)
                }
            ) {}
        }
        val shutter = compose.onNodeWithContentDescription("拍照")
        shutter.assertIsNotEnabled().performClick()
        assertEquals(0, requests)
        compose.runOnIdle { state.value = CaptureUiState(ready = true) }
        shutter.assertIsEnabled().performClick()
        shutter.assertIsNotEnabled().performClick()
        compose.onNodeWithText("正在保存…").assertExists()
        assertEquals(1, requests)
        compose.runOnIdle {
            state.value = CaptureUiState(ready = true, message = "保存失败，请重试。")
        }
        compose.onNodeWithText("保存失败，请重试。").assertExists()
        shutter.assertIsEnabled().performClick()
        assertEquals(2, requests)
    }
}
