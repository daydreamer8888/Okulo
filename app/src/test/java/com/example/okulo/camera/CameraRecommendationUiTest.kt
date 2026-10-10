package com.example.okulo.camera

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.okulo.photo.recommendation
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
class CameraRecommendationUiTest {
    @get:Rule val compose = createComposeRule()

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
        compose.onNodeWithContentDescription("恢复推荐").assertIsNotEnabled()
        compose.onNodeWithContentDescription("推荐构图").performClick()
        compose.onNodeWithContentDescription("正在推荐构图").assertExists()
        compose.onNodeWithContentDescription("正在更新裁剪评分").assertDoesNotExist()
        compose.onNodeWithText("2.00").assertExists()
        assertEquals(scores, compose.onNodeWithTag("composition-scores").fetchSemanticsNode().boundsInRoot)
        assertEquals(shutter, compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("恢复推荐").assertIsNotEnabled()
        compose.onNodeWithContentDescription("取消推荐").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("拍照").assertIsEnabled().performClick()
        assertEquals(1, cancellations)
        assertEquals(1, captures)
    }
}
