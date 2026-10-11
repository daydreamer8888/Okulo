package com.example.okulo.camera

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraRecommendationUiTest {
    @get:Rule val compose = createComposeRule()

    private fun gatedRecommendation(gate: WorkGate): com.example.okulo.composition.CompositionResult {
        gate.block()
        return recommendation()
    }

    private fun openRecommendationMenu() {
        compose.onNodeWithContentDescription("推荐构图").performClick()
    }

    @Test
    fun completedRecommendationsOfferRestoreAndCloseInTheButtonMenu() {
        val result = recommendation()
        val state = mutableStateOf(CameraCompositionState(result, result.crop, 2.8f, 3.13f))
        var restores = 0
        var dismissals = 0
        var recommendations = 0
        compose.setContent {
            OkuloTheme(darkTheme = true) {
                CameraLayout(
                    onImportPhoto = {},
                    capture = CaptureUiState(ready = true),
                    composition = state.value,
                    compositionActions = CameraCompositionActions(
                        { recommendations++ },
                        { restores++ },
                        { dismissals++ }
                    ),
                    showScores = true
                ) {}
            }
        }
        compose.onNodeWithText("恢复").assertDoesNotExist()
        openRecommendationMenu()
        compose.onNodeWithText("恢复").assertIsNotEnabled()
        compose.onNodeWithText("关闭").assertIsEnabled()
        assertEquals(0, recommendations)
        compose.runOnIdle { state.value = state.value.copy(crop = result.crop.copy(left = 0.05f)) }
        compose.onNodeWithText("恢复").assertIsEnabled().performClick()
        assertEquals(1, restores)
        compose.onNodeWithText("关闭").assertDoesNotExist()
        openRecommendationMenu()
        compose.onNodeWithText("关闭").performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun signedScoresAndLoadingKeepTheCardAndLabelsInPlace() {
        val result = recommendation()
        val state = mutableStateOf(CameraCompositionState(result, result.crop, 2.8f, 3.13f))
        compose.setContent {
            OkuloTheme(darkTheme = true) {
                CameraLayout({}, composition = state.value, showScores = true) {}
            }
        }
        val card = compose.onNodeWithTag("composition-scores").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, card.center.x, 1f)
        val original = compose.onNodeWithText("原图").fetchSemanticsNode().boundsInRoot
        val crop = compose.onNodeWithText("裁剪").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state.value = state.value.copy(originalScore = -2.5f, cropScore = -11.75f) }
        compose.onNodeWithText("-11.75").assertExists()
        assertEquals(card, compose.onNodeWithTag("composition-scores").fetchSemanticsNode().boundsInRoot)
        assertEquals(original, compose.onNodeWithText("原图").fetchSemanticsNode().boundsInRoot)
        assertEquals(crop, compose.onNodeWithText("裁剪").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = state.value.copy(cropScore = null, scoring = true) }
        compose.onNodeWithContentDescription("正在更新裁剪评分").assertExists()
        assertEquals(card, compose.onNodeWithTag("composition-scores").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = state.value.copy(cropScore = 1.11f, scoring = false) }
        compose.onNodeWithText("1.11").assertExists()
        assertEquals(card, compose.onNodeWithTag("composition-scores").fetchSemanticsNode().boundsInRoot)
        assertEquals(crop, compose.onNodeWithText("裁剪").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun recommendationProgressKeepsScoresAndShutterStableAndCanBeCancelled() {
        val result = recommendation()
        val state = mutableStateOf(
            CameraCompositionState(result, result.crop, result.originalScore, result.cropScore)
        )
        var cancellations = 0
        var captures = 0
        compose.setContent {
            OkuloTheme(darkTheme = true) {
                CameraLayout(
                    onImportPhoto = {},
                    capture = CaptureUiState(ready = true),
                    onCapture = { captures++ },
                    composition = state.value,
                    showScores = true,
                    compositionActions = CameraCompositionActions(
                        recommend = { state.value = state.value.copy(busy = true, scoring = false, cropScore = null) },
                        dismiss = { cancellations++ }
                    )
                ) {}
            }
        }
        val scores = compose.onNodeWithTag("composition-scores").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state.value = state.value.copy(busy = true, scoring = false, cropScore = null) }
        compose.onNodeWithContentDescription("正在推荐构图").assertExists()
        compose.onNodeWithContentDescription("正在更新裁剪评分").assertDoesNotExist()
        compose.onNodeWithText("2.00").assertExists()
        assertEquals(scores, compose.onNodeWithTag("composition-scores").fetchSemanticsNode().boundsInRoot)
        assertEquals(shutter, compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("恢复").assertDoesNotExist()
        compose.onNodeWithContentDescription("取消推荐").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("拍照").assertIsEnabled().performClick()
        assertEquals(1, cancellations)
        assertEquals(1, captures)
    }

    @Test
    fun recommendationOverlaysTheContinuingPreviewAndScoresAfterDragging() {
        val analyzer = TestAnalyzer()
        val gate = WorkGate()
        analyzer.analyze = { gatedRecommendation(gate) }
        val session = CameraComposition(analyzer)
        val preview = mutableStateOf("实时画面一")
        fun frame() = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        try {
            compose.setContent {
                val state = session.state.collectAsState().value
                OkuloTheme(darkTheme = true) {
                    CameraLayout(
                        onImportPhoto = {},
                        capture = CaptureUiState(ready = true),
                        composition = state,
                        compositionActions = CameraCompositionActions(
                            { session.recommend(frame()) },
                            session::restore,
                            session::dismiss
                        ),
                        showScores = true
                    ) {
                        CameraViewfinder(
                            state.crop,
                            CropActions(session::updateCrop, { session.evaluateCrop(frame()) })
                        ) {
                            Box(Modifier.fillMaxSize().testTag("live-preview")) { Text(preview.value) }
                        }
                    }
                }
            }
            openRecommendationMenu()
            gate.awaitEntry()
            compose.runOnIdle { preview.value = "实时画面二" }
            compose.onNodeWithText("实时画面二").assertExists()
            compose.onNodeWithTag("camera-crop").assertDoesNotExist()
            gate.release()
            await { !session.state.value.busy }
            compose.onNodeWithTag("camera-crop").assertExists().performTouchInput {
                swipe(center, center + Offset(width * 0.05f, 0f))
            }
            await { !session.state.value.scoring }
            assertEquals(1, analyzer.evaluationCalls.get())
            compose.onNodeWithText("4.00").assertExists()
            openRecommendationMenu()
            compose.onNodeWithText("恢复").assertIsEnabled().performClick()
            assertEquals(recommendation().crop, session.state.value.crop)
            compose.onNodeWithText("3.00").assertExists()
            openRecommendationMenu()
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithTag("camera-crop").assertDoesNotExist()
            compose.onNodeWithTag("live-preview").assertExists()
        } finally {
            gate.release()
            session.close()
            assertTrue(analyzer.closed.await(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failedScoringKeepsTheCropStopsProgressAndReportsTheFailureNearTheToolbar() {
        val result = recommendation()
        compose.setContent {
            OkuloTheme(darkTheme = true) {
                CameraLayout(
                    onImportPhoto = {},
                    capture = CaptureUiState(ready = true),
                    composition = CameraCompositionState(
                        recommendation = result,
                        crop = result.crop.copy(right = 0.7f),
                        originalScore = result.originalScore,
                        error = "评分失败，请重试"
                    ),
                    showScores = true
                ) {}
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("2.00").assertExists()
        compose.onNodeWithContentDescription("正在更新裁剪评分").assertDoesNotExist()
        val failure = compose.onNodeWithText("评分失败，请重试").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
        assertTrue(failure.bottom < shutter.top)
        compose.onNodeWithContentDescription("推荐构图").assertIsEnabled()
        compose.mainClock.autoAdvance = true
        openRecommendationMenu()
        compose.onNodeWithText("恢复").assertIsEnabled()
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
