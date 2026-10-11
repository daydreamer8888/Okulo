package com.example.okulo.camera

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.okulo.photo.TestAnalyzer
import com.example.okulo.ui.crop.CropActions
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraSubjectUiTest {
    @get:Rule val compose = createComposeRule()

    private fun showCamera(
        composition: CameraComposition,
        frames: CameraFrameActions,
        actions: CameraSubjectActions
    ) {
        compose.setContent {
            val state = composition.state.collectAsState().value
            OkuloTheme(darkTheme = true) {
                CameraLayout(
                    onImportPhoto = {},
                    capture = CaptureUiState(ready = true),
                    composition = state,
                    compositionActions = CameraCompositionActions(
                        frames::recommend,
                        composition::restore,
                        composition::dismiss
                    ),
                    subjectActions = actions
                ) {
                    CameraViewfinder(state.crop, CropActions(), subjectState = state, subjectActions = actions) {}
                }
            }
        }
    }

    @Test
    fun selectingASubjectShowsTheDrawingControlWithoutInstructionText() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OkuloTheme {
                Box(Modifier.size(300.dp, 400.dp)) {
                    CameraSubjectOverlay(
                        CameraCompositionState(selectingSubject = true),
                        CameraSubjectActions()
                    )
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag("subject-selection").assertExists()
        compose.onNodeWithText("框选主体").assertDoesNotExist()
    }

    @Test
    fun freezingForSubjectSelectionShowsItsOwnProgressAndAllowsCancelling() {
        var cancellations = 0
        compose.setContent {
            OkuloTheme(darkTheme = true) {
                CameraLayout(
                    {},
                    capture = CaptureUiState(ready = true),
                    composition = CameraCompositionState(busy = true, selectingSubject = true),
                    subjectActions = CameraSubjectActions(cancel = { cancellations++ })
                ) {}
            }
        }
        compose.onNodeWithContentDescription("正在定格画面").assertExists()
        compose.onNodeWithContentDescription("正在推荐构图").assertDoesNotExist()
        compose.onNodeWithContentDescription("推荐构图").assertIsNotEnabled()
        compose.onNodeWithContentDescription("取消框选").assertIsEnabled().performClick()
        assertEquals(1, cancellations)
    }

    @Test
    fun narrowCameraKeepsBothCompositionControlsSeparateFromTheCenteredShutter() {
        compose.setContent {
            OkuloTheme(darkTheme = true) {
                Box(Modifier.size(320.dp, 600.dp).testTag("camera-layout")) {
                    CameraLayout({}, capture = CaptureUiState(ready = true)) {}
                }
            }
        }
        val camera = compose.onNodeWithTag("camera-layout").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
        val subject = compose.onNodeWithContentDescription("框选主体").fetchSemanticsNode().boundsInRoot
        val recommend = compose.onNodeWithContentDescription("推荐构图").fetchSemanticsNode().boundsInRoot
        val recent = compose.onNodeWithContentDescription("查看最新照片").fetchSemanticsNode().boundsInRoot
        assertTrue(recent.right < shutter.left)
        assertTrue(shutter.left - recent.right <= with(compose.density) { 24.dp.toPx() })
        assertEquals(camera.center.x, shutter.center.x, 1f)
        assertTrue(shutter.right <= subject.left)
        assertTrue(subject.right <= recommend.left)
        assertTrue(recommend.right <= camera.right)
    }

    @Test
    fun subjectSelectionSharesButtonGeometryAndPreservesTheResultWhenCancelled() {
        val engine = TestAnalyzer()
        val composition = CameraComposition(engine)
        val frames = CameraFrameActions(composition, { Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888) })
        val actions = CameraSubjectActions(
            frames::selectSubject,
            composition::cancelSubjectSelection,
            composition::commitSubject,
            composition::clearSubject
        )
        try {
            showCamera(composition, frames, actions)
            val subjectButton = compose.onNodeWithContentDescription("框选主体").fetchSemanticsNode().boundsInRoot
            val recommendButton = compose.onNodeWithContentDescription("推荐构图").fetchSemanticsNode().boundsInRoot
            assertEquals(subjectButton.size, recommendButton.size)
            val shutter = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription("框选主体").performClick()
            compose.onNodeWithContentDescription("推荐构图").assertIsNotEnabled()
            compose.onNodeWithTag("subject-selection").performTouchInput {
                swipe(Offset(width * 0.7f, height * 0.6f), Offset(width * 0.2f, height * 0.3f))
            }
            val region = checkNotNull(composition.state.value.retainedRegion)
            assertEquals(0.2f, region.left, 0.02f)
            assertEquals(0.7f, region.right, 0.02f)
            compose.onNodeWithContentDescription("框选主体").performClick()
            compose.onNodeWithText("重新框选").performClick()
            compose.onNodeWithContentDescription("取消框选").performClick()
            assertEquals(region, composition.state.value.retainedRegion)
            compose.onNodeWithContentDescription("推荐构图").assertIsEnabled().performClick()
            compose.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                !composition.state.value.busy
            }
            compose.onNodeWithContentDescription("框选主体").assertIsNotEnabled()
            val crop = composition.state.value.crop
            assertTrue(crop != null)
            assertEquals(shutter, compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithContentDescription("推荐构图").performClick()
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithContentDescription("框选主体").assertIsEnabled().performClick()
            compose.onNodeWithContentDescription("取消框选").assertExists()
            compose.onNodeWithTag("camera-crop").assertDoesNotExist()
        } finally {
            composition.close()
            assertTrue(engine.closed.await(10, TimeUnit.SECONDS))
        }
    }
}
