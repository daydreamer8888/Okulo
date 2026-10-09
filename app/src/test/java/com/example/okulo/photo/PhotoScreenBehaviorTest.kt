package com.example.okulo.photo

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.okulo.composition.CropAspect
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
    fun everyAnalysisShowsProgressWithoutMovingTheExistingEditor() {
        val state = mutableStateOf(PhotoState(photo = image(), result = recommendation()))
        val progress = androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
        )
        compose.setContent { OkuloTheme { PhotoScreen(state.value, {}, {}, {}) } }
        val bounds = compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot
        compose.onNode(progress).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        compose.onNode(progress).assertExists()
        assertEquals(bounds, compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = state.value.copy(busy = false) }
        compose.onNode(progress).assertDoesNotExist()
        compose.runOnIdle { state.value = PhotoState(busy = true) }
        compose.onNode(progress).assertExists()
    }

    @Test
    fun emptyScreenFocusesOnPickingBeforeEditingActionsAppear() {
        compose.setContent { OkuloTheme { PhotoScreen(PhotoState(), {}, {}, {}) } }
        compose.onNodeWithText("选择照片").assertIsEnabled()
        compose.onNodeWithContentDescription("分析构图").assertDoesNotExist()
        compose.onNodeWithContentDescription("保存").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回拍摄").assertIsEnabled()
    }

    @Test
    fun manualScoringKeepsEditingControlsStableAndAvailable() = PhotoTestHarness().use { fixture ->
        fixture.select()
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy }
        fixture.model.updateCrop(CropBox(0.2f, 0.2f, 0.8f, 0.6f))
        val gate = fixture.gate()
        fixture.analyzer.evaluate = { crop ->
            gate.block()
            recommendation().copy(crop = crop, cropScore = 4f)
        }
        compose.setContent {
            OkuloTheme { PhotoScreen(fixture.model.state.collectAsState().value, {}, {}, {}) }
        }
        val analyzeBounds = compose.onNodeWithContentDescription("分析构图").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { fixture.model.evaluateCrop() }
        gate.awaitEntry()
        compose.onNodeWithText("自由").assertIsEnabled()
        compose.onNodeWithContentDescription("分析构图").assertIsEnabled()
        compose.onNodeWithContentDescription("取消").assertDoesNotExist()
        compose.onNodeWithContentDescription("保存").assertIsEnabled()
        compose.onNodeWithContentDescription("更多").assertIsEnabled()
        assertEquals(analyzeBounds, compose.onNodeWithContentDescription("分析构图").fetchSemanticsNode().boundsInRoot)
        gate.release()
        fixture.await { fixture.model.state.value.manualScore != null }
        compose.onNodeWithContentDescription("保存").assertIsEnabled()
        Unit
    }

    @Test
    fun editActionsFollowAspectSelectionAndTransformsRequireOpeningMore() {
        val state = PhotoState(photo = image(), manualCrop = CropBox(0.2f, 0.2f, 0.8f, 0.8f))
        var operation: PhotoOperation? = null
        compose.setContent {
            OkuloTheme { PhotoScreen(state, {}, {}, {}, onTransform = { operation = it }) }
        }
        val aspect = compose.onNodeWithText("自由").fetchSemanticsNode().boundsInRoot
        val analyze = compose.onNodeWithContentDescription("分析构图").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithContentDescription("保存").fetchSemanticsNode().boundsInRoot
        val editor = compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot
        assertTrue(analyze.top >= aspect.bottom)
        assertEquals(analyze.top, save.top, 1f)
        assertTrue(analyze.bottom <= editor.top)
        compose.onNodeWithContentDescription("向右旋转").assertDoesNotExist()
        compose.onNodeWithText("向右旋转").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("向右旋转").performClick()
        assertEquals(PhotoOperation.RotateClockwise, operation)
        compose.onNodeWithText("向右旋转").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("水平翻转").performClick()
        assertEquals(PhotoOperation.FlipHorizontal, operation)
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("垂直翻转").performClick()
        assertEquals(PhotoOperation.FlipVertical, operation)
    }

    @Test
    fun aspectCanBeChosenBeforeAnalysisAndNewSearchKeepsTheEditorStationary() {
        val state = mutableStateOf(PhotoState(photo = image()))
        var analyses = 0
        compose.setContent {
            OkuloTheme {
                PhotoScreen(
                    state.value,
                    {},
                    { analyses++ },
                    {},
                    onAspect = { state.value = state.value.copy(aspect = it) }
                )
            }
        }
        compose.onNodeWithText("16:9").assertDoesNotExist()
        compose.onNodeWithContentDescription("裁剪比例").performClick()
        compose.onNodeWithText("16:9").performClick()
        assertEquals(CropAspect.Wide, state.value.aspect)
        compose.onNodeWithText("自由").assertDoesNotExist()
        compose.onNodeWithText("16:9").assertExists()
        compose.onNodeWithContentDescription("裁剪比例").performClick()
        compose.onNode(hasText("原图") and hasAnyAncestor(isPopup())).performClick()
        assertEquals(CropAspect.Original, state.value.aspect)
        compose.onNodeWithContentDescription("裁剪比例").performClick()
        compose.onNodeWithText("自由").performClick()
        assertEquals(CropAspect.Free, state.value.aspect)
        compose.onNodeWithText("16:9").assertDoesNotExist()
        compose.onNodeWithContentDescription("分析构图").performClick()
        assertEquals(1, analyses)
        compose.runOnIdle {
            state.value = state.value.copy(manualCrop = CropBox(0.1f, 0.2f, 0.9f, 0.6f), aspect = CropAspect.Free)
        }
        val editorBounds = compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("分析构图").assertDoesNotExist()
        compose.onNodeWithContentDescription("分析构图").performClick()
        assertEquals(2, analyses)
        compose.onNodeWithContentDescription("恢复推荐").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        compose.onNodeWithContentDescription("分析构图").assertDoesNotExist()
        assertEquals(editorBounds, compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("取消").performClick()
    }

    @Test
    fun optedInScoresRemainVisibleForAFreeCropWithoutACurrentRecommendation() {
        val state = PhotoState(
            photo = image(),
            aspect = CropAspect.Free,
            manualCrop = CropBox(0.1f, 0.2f, 0.9f, 0.6f),
            manualScore = 4f,
            manualOriginalScore = 2f
        )
        compose.setContent {
            OkuloTheme { PhotoScreen(state, {}, {}, {}, showModelScores = true) }
        }
        compose.onNodeWithText("原图 2.000 · 裁剪 4.000").assertExists()
        compose.onNodeWithContentDescription("分析构图").assertExists()
        compose.onNodeWithContentDescription("恢复推荐").assertDoesNotExist()
    }

    @Test
    fun saveRequiresACropAndCannotBeRepeatedWhileWriting() {
        val state = mutableStateOf(PhotoState())
        val saving = mutableStateOf(false)
        var saves = 0
        org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication())
            .grantPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        compose.setContent {
            OkuloTheme {
                PhotoScreen(
                    state.value,
                    {},
                    {},
                    {},
                    onSave = {
                        saves++
                        saving.value = true
                    },
                    saving = saving.value
                )
            }
        }
        compose.onNodeWithContentDescription("保存").assertDoesNotExist()
        compose.runOnIdle { state.value = PhotoState(photo = image(), result = recommendation()) }
        compose.onNodeWithContentDescription("保存").performClick()
        compose.onNodeWithContentDescription("保存").assertIsNotEnabled()
        assertEquals(1, saves)
        compose.runOnIdle { saving.value = false }
        compose.onNodeWithContentDescription("保存").performClick()
        assertEquals(2, saves)
    }

    @Test
    fun analysisCancelAndRetryRouteToTheCorrectAction() {
        val state = mutableStateOf(PhotoState())
        var analysis = 0
        var cancel = 0
        var retry = 0
        compose.setContent {
            OkuloTheme {
                PhotoScreen(state.value, {}, { analysis++ }, { cancel++ }, onRetry = { retry++ })
            }
        }
        compose.onNodeWithContentDescription("分析构图").assertDoesNotExist()
        compose.runOnIdle { state.value = PhotoState(photo = image()) }
        compose.onNodeWithContentDescription("分析构图").performClick()
        assertEquals(1, analysis)
        compose.runOnIdle { state.value = state.value.copy(busy = true, status = "正在分析照片…") }
        compose.onNodeWithContentDescription("分析构图").assertDoesNotExist()
        compose.onNodeWithContentDescription("取消").performClick()
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
            OkuloTheme { PhotoScreen(state.value, {}, { analysis++ }, {}, cropActions = actions) }
        }
        compose.onNodeWithText("重新分析").assertDoesNotExist()
        compose.onNodeWithContentDescription("恢复推荐").assertDoesNotExist()
        compose.onNodeWithText("分析完成").assertDoesNotExist()
        val editorBounds = compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            state.value = state.value.copy(manualCrop = CropBox(0.2f, 0.2f, 0.6f, 0.6f), status = "裁剪评分已更新")
        }
        assertEquals(editorBounds, compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        assertEquals(editorBounds, compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("恢复推荐").performClick()
        assertEquals(1, restores)
        assertEquals(0, analysis)
        compose.onNodeWithContentDescription("恢复推荐").assertDoesNotExist()
        compose.onNodeWithText("裁剪评分已更新").assertDoesNotExist()
    }

    @Test
    fun smallCropWarningAppearsBelowTheEditorAndClearsWhenEnlarged() {
        val state = mutableStateOf(PhotoState(photo = image(), manualCrop = CropBox(0f, 0f, 1f, 0.5f)))
        compose.setContent { OkuloTheme { PhotoScreen(state.value, {}, {}, {}) } }
        val bounds = compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("裁剪范围较小").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(manualCrop = CropBox(0f, 0f, 0.7f, 0.7f)) }
        compose.onNodeWithText("裁剪范围较小").assertExists()
        assertEquals(bounds, compose.onNodeWithTag("crop-editor").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("保存").assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(manualCrop = CropBox.FullFrame) }
        compose.onNodeWithText("裁剪范围较小").assertDoesNotExist()
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
        val showScores = mutableStateOf(false)
        compose.setContent {
            OkuloTheme { PhotoScreen(state, {}, {}, {}, showModelScores = showScores.value) }
        }
        compose.onNodeWithText("查看模型评分").assertDoesNotExist()
        compose.onNodeWithText("原图 2.000 · 裁剪 4.000").assertDoesNotExist()
        compose.onNodeWithText("分析用时 1.20 秒 · 比较了 250 个方案").assertDoesNotExist()
        compose.onNodeWithText("裁剪评分用时 0.04 秒").assertDoesNotExist()
        compose.onNodeWithText("拖动框内移动，拖动四角或双指缩放").assertDoesNotExist()
        compose.onNodeWithText("保留原图 16%").assertDoesNotExist()
        compose.onNodeWithText("裁剪范围较小").assertExists()
        compose.runOnIdle { showScores.value = true }
        compose.onNodeWithText("原图 2.000 · 裁剪 4.000").assertExists()
        compose.onNodeWithText("裁剪评分用时 0.04 秒").assertDoesNotExist()
        compose.runOnIdle { showScores.value = false }
        compose.onNodeWithText("原图 2.000 · 裁剪 4.000").assertDoesNotExist()
    }

    private fun image(): Bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
}
