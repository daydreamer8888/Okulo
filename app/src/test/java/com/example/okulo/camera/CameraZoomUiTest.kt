package com.example.okulo.camera

import androidx.camera.core.ZoomState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.example.okulo.ui.theme.OkuloTheme
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
class CameraZoomUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactSliderUsesDeviceStateWithoutMovingThePreviewOrShutter() {
        val state = mutableStateOf<ZoomState>(DeviceZoom(1f, 1.8f, 1f, 0f))
        val requested = mutableListOf<Float>()
        compose.setContent {
            OkuloTheme(darkTheme = true) {
                CameraLayout({}, capture = CaptureUiState(ready = true)) {
                    Box(Modifier.fillMaxSize().testTag("preview")) {
                        Text("实时取景")
                        CameraZoomControls(
                            state.value,
                            {},
                            { requested += it },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        val preview = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("2×").assertDoesNotExist()
        compose.onNodeWithContentDescription("变焦，当前 1×").performClick()
        compose.onNodeWithTag("zoom-panel").assertHeightIsEqualTo(56.dp)
        compose.onNodeWithContentDescription("变焦倍率").performSemanticsAction(SemanticsActions.SetProgress) {
            it(1f)
        }
        assertEquals(listOf(1f), requested)
        compose.onNodeWithText("1×").assertExists()
        compose.runOnIdle { state.value = DeviceZoom(1f, 1.8f, 1.8f, 1f) }
        compose.onNodeWithText("1.8×").assertExists()
        assertEquals(preview, compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot)
        assertEquals(shutter, compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("收起变焦调节").performClick()
        compose.onNodeWithTag("zoom-panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("变焦，当前 1.8×").assertExists()
    }
}
