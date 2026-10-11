package com.example.okulo.camera

import android.graphics.PointF
import androidx.camera.view.CameraController
import androidx.camera.view.TapToFocusInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
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
class CameraMeteringUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun focusAndExposureStayInsideThePreviewAndHideAfterThreeSecondsWithoutResettingExposure() {
        val state = mutableStateOf(
            CameraMeteringState(
                TapToFocusInfo(CameraController.TAP_TO_FOCUS_STARTED, PointF(900f, 1f)),
                -2..2,
                0
            )
        )
        val requests = mutableListOf<Float>()
        compose.setContent {
            OkuloTheme {
                Box(Modifier.size(300.dp, 400.dp).testTag("preview")) {
                    CameraMeteringControls(state.value) { requests += it }
                }
            }
        }
        val preview = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
        val focus = compose.onNodeWithContentDescription("正在对焦").fetchSemanticsNode().boundsInRoot
        val slider = compose.onNodeWithTag("exposure-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(focus.left >= preview.left && focus.right <= preview.right)
        assertTrue(focus.top >= preview.top && focus.bottom <= preview.bottom)
        assertTrue(slider.left >= preview.left && slider.right <= preview.right)
        assertTrue(slider.top >= preview.top && slider.bottom <= preview.bottom)
        compose.onNodeWithContentDescription("曝光补偿").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        assertEquals(listOf(2f), requests)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithContentDescription("正在对焦").assertExists()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithContentDescription("正在对焦").assertDoesNotExist()
        compose.onNodeWithTag("exposure-panel").assertDoesNotExist()
        assertEquals(listOf(2f), requests)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            state.value = CameraMeteringState(TapToFocusInfo(CameraController.TAP_TO_FOCUS_FAILED, PointF(50f, 50f)))
        }
        compose.onNodeWithContentDescription("对焦失败").assertExists()
        compose.onNodeWithContentDescription("曝光补偿").assertDoesNotExist()
    }
}
