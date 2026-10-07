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
import org.junit.Assert.assertTrue
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
    fun scoresTrackManualEditsAndRestoreTheRecommendation() {
        val bitmap = image()
        val state = mutableStateOf(PhotoState(photo = bitmap, result = recommendation()))
        var restored = false
        val actions = CropActions(restore = {
            restored = true
            state.value = state.value.copy(manualCrop = null, manualScore = null, manualMillis = null)
        })
        compose.setContent {
            OkuloTheme { PhotoScreen(state.value, {}, {}, {}, {}, cropActions = actions) }
        }
        compose.onNodeWithText("原图 2.000 · 裁剪 3.000").assertDoesNotExist()
        compose.onNodeWithText("查看模型评分").performScrollTo().performClick()
        compose.onNodeWithText("原图 2.000 · 裁剪 3.000").assertExists()
        compose.onNodeWithText("模型准备 0.15 秒").assertExists()
        compose.runOnIdle {
            state.value = state.value.copy(manualCrop = CropBox(0.2f, 0.2f, 0.6f, 0.6f))
        }
        compose.onNodeWithText("原图 2.000 · 裁剪 3.000").assertDoesNotExist()
        compose.onNodeWithText("裁剪评分待更新").assertExists()
        compose.onNodeWithText("裁剪范围较小").assertExists()
        compose.runOnIdle { state.value = state.value.copy(manualScore = 4f, manualMillis = 35) }
        compose.onNodeWithText("原图 2.000 · 裁剪 4.000").assertExists()
        compose.onNodeWithText("裁剪评分用时 0.04 秒").assertExists()
        compose.onNodeWithText("恢复推荐").performScrollTo().performClick()
        assertTrue(restored)
        compose.onNodeWithText("原图 2.000 · 裁剪 3.000").assertExists()
        compose.onNodeWithText("裁剪范围较小").assertDoesNotExist()
        compose.onNodeWithText("恢复推荐").assertDoesNotExist()
        compose.onNodeWithText("收起模型评分").performScrollTo().performClick()
        compose.onNodeWithText("原图 2.000 · 裁剪 3.000").assertDoesNotExist()
    }

    @Test
    fun changingPhotosResetsExpandedScoresAndHidesOldTiming() {
        val state = mutableStateOf(PhotoState(photo = image(), result = recommendation()))
        compose.setContent { OkuloTheme { PhotoScreen(state.value, {}, {}, {}, {}) } }
        compose.onNodeWithText("查看模型评分").performScrollTo().performClick()
        compose.onNodeWithText("原图 2.000 · 裁剪 3.000").assertExists()
        compose.runOnIdle {
            state.value = PhotoState(photo = image(), result = recommendation().copy(loadMillis = 0))
        }
        compose.onNodeWithText("收起模型评分").assertDoesNotExist()
        compose.onNodeWithText("查看模型评分").assertExists()
        compose.onNodeWithText("模型准备 0.15 秒").assertDoesNotExist()
    }

    private fun image(): Bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
}
