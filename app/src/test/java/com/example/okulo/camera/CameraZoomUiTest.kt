package com.example.okulo.camera

import android.graphics.Bitmap
import android.os.Looper
import androidx.camera.core.ZoomState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.photo.TestAnalyzer
import com.example.okulo.photo.WorkGate
import com.example.okulo.photo.recommendation
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
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

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

    @Test
    fun choosingTwoTimesKeepsBothShortcutPositionsAndAllowsReturningToOneTimes() {
        val state = mutableStateOf<ZoomState>(DeviceZoom(1f, 10f, 1f, 0f))
        compose.setContent {
            OkuloTheme(darkTheme = true) {
                CameraZoomControls(
                    state.value,
                    { state.value = DeviceZoom(1f, 10f, it, 0f) },
                    {}
                )
            }
        }
        val one = compose.onNodeWithText("1×").fetchSemanticsNode().boundsInRoot
        val two = compose.onNodeWithText("2×").fetchSemanticsNode().boundsInRoot
        assertTrue(one.center.x < two.center.x)
        compose.onNodeWithText("2×").performClick()
        compose.onNodeWithContentDescription("变焦，当前 2×").assertExists()
        assertEquals(one, compose.onNodeWithText("1×").fetchSemanticsNode().boundsInRoot)
        assertEquals(two, compose.onNodeWithText("2×").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("1×").performClick()
        compose.onNodeWithContentDescription("变焦，当前 1×").assertExists()
        assertEquals(one, compose.onNodeWithText("1×").fetchSemanticsNode().boundsInRoot)
        assertEquals(two, compose.onNodeWithText("2×").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun pinchingChangesCameraZoomAndRejectsAnOldRecommendation() {
        val analyzer = TestAnalyzer()
        val session = CameraComposition(analyzer)
        val gate = WorkGate()
        lateinit var zoom: CameraZoom
        zoom = CameraZoom(
            setRatio = { ratio ->
                zoom.update(DeviceZoom(1f, 3f, ratio, 0.5f))
                CallbackToFutureAdapter.getFuture<Void> { it.set(null) }
            },
            setLinear = { error("Pinch should request a ratio") },
            executor = Executor { it.run() },
            onChanged = session::invalidateScene
        )
        zoom.update(DeviceZoom(1f, 3f, 1f, 0f))
        val first = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        val pending = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        try {
            session.recommend(first, AnalysisMode.Fast)
            await { !session.state.value.busy }
            compose.setContent {
                val state = session.state.collectAsState().value
                OkuloTheme(darkTheme = true) {
                    CameraViewfinder(
                        state.crop,
                        CropActions(session::updateCrop, {}),
                        gestureModifier = Modifier.cameraPinchZoom(zoom).testTag("zoom-viewfinder")
                    ) { Text("实时取景") }
                }
            }
            compose.onNodeWithTag("camera-crop").assertExists()
            analyzer.analyze = { _, _ ->
                gate.block()
                recommendation()
            }
            compose.runOnIdle { session.recommend(pending, AnalysisMode.Fast) }
            gate.awaitEntry()
            compose.onNodeWithTag("zoom-viewfinder").performTouchInput {
                down(0, Offset(width * 0.35f, height * 0.5f))
                down(1, Offset(width * 0.65f, height * 0.5f))
                moveTo(0, Offset(width * 0.2f, height * 0.5f))
                moveTo(1, Offset(width * 0.8f, height * 0.5f))
                up(0)
                up(1)
            }
            assertTrue(zoom.state!!.zoomRatio > 1.5f)
            compose.onNodeWithTag("camera-crop").assertDoesNotExist()
            assertEquals(null, session.state.value.crop)
            gate.release()
            await { pending.isRecycled }
            assertEquals(null, session.state.value.recommendation)
        } finally {
            gate.release()
            session.close()
            assertTrue(analyzer.closed.await(10, TimeUnit.SECONDS))
        }
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
