package com.example.okulo.camera

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
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
