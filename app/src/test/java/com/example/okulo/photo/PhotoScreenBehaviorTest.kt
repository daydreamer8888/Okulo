package com.example.okulo.photo

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CropBox
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoScreenBehaviorTest {
    @get:Rule val compose = createComposeRule()
    private val originalLocale = Locale.getDefault()

    @Before
    fun useEnglishNumberFormatting() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    @Test
    fun analysisCancelRetryAndModeControlsRouteToTheCorrectAction() {
        val state = mutableStateOf(PhotoState())
        var analysis = 0
        var cancel = 0
        var retry = 0
        var mode: AnalysisMode? = null
        compose.setContent {
            OkuloTheme {
                PhotoScreen(state.value, {}, { mode = it }, { analysis++ }, { cancel++ }, onRetry = { retry++ })
            }
        }
        compose.onNodeWithText("分析构图").assertIsNotEnabled()
        compose.onNodeWithText("快速分析").performClick()
        assertEquals(AnalysisMode.Fast, mode)
        compose.runOnIdle { state.value = PhotoState(photo = image()) }
        compose.onNodeWithText("分析构图").performClick()
        assertEquals(1, analysis)
        compose.runOnIdle { state.value = state.value.copy(busy = true, status = "正在分析照片…") }
        compose.onNodeWithText("分析构图").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        assertEquals(1, cancel)
        compose.runOnIdle { state.value = state.value.copy(busy = false, error = "照片不可访问") }
        compose.onNodeWithText("照片不可访问").assertExists()
        compose.onNodeWithText("重试").performClick()
        assertEquals(1, retry)
        assertEquals(1, analysis)
    }

    @Test
    fun completedRecommendationCanBeRestoredWithoutRepeatingAnalysis() {
        val state = mutableStateOf(PhotoState(photo = image(), result = recommendation(), status = "分析完成"))
        var analysis = 0
        var restores = 0
        val actions = CropActions(restore = {
            restores++
            state.value = state.value.copy(manualCrop = null, manualScore = null)
        })
        compose.setContent {
            OkuloTheme { PhotoScreen(state.value, {}, {}, { analysis++ }, {}, cropActions = actions) }
        }
        compose.onNodeWithText("重新分析").assertDoesNotExist()
        compose.onNodeWithText("恢复推荐").assertDoesNotExist()
        compose.onNodeWithText("分析完成").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(manualCrop = CropBox(0.2f, 0.2f, 0.6f, 0.6f), status = "裁剪评分已更新")
        }
        compose.onNodeWithText("恢复推荐").performScrollTo().performClick()
        assertEquals(1, restores)
        assertEquals(0, analysis)
        compose.onNodeWithText("恢复推荐").assertDoesNotExist()
        compose.onNodeWithText("裁剪评分已更新").assertDoesNotExist()
    }

    @Test
    fun normalEditingOmitsModelDiagnosticsAndGestureInstructions() {
        val state = PhotoState(
            photo = image(),
            result = recommendation(),
            manualCrop = CropBox(0.2f, 0.2f, 0.6f, 0.6f),
            manualScore = 4f,
            manualMillis = 35
        )
        compose.setContent { OkuloTheme { PhotoScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithText("查看模型评分").assertDoesNotExist()
        compose.onNodeWithText("原图 2.000 · 裁剪 4.000").assertDoesNotExist()
        compose.onNodeWithText("分析用时 1.20 秒 · 比较了 250 个方案").assertDoesNotExist()
        compose.onNodeWithText("裁剪评分用时 0.04 秒").assertDoesNotExist()
        compose.onNodeWithText("拖动框内移动，拖动四角或双指缩放").assertDoesNotExist()
        compose.onNodeWithText("保留原图 16%").assertDoesNotExist()
        compose.onNodeWithText("裁剪范围较小").assertDoesNotExist()
    }

    private fun image(): Bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
}
