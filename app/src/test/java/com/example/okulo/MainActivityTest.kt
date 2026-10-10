package com.example.okulo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.photo.PhotoViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun retainingCaptureOriginalIsDefaultAndTheChoiceSurvivesRecreation() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("使用推荐构图拍照时").assertExists()
        compose.onNodeWithText("同时保存原图").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("同时保存原图").performScrollTo().assertIsOff()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("同时保存原图").performScrollTo().assertIsOff().performClick().assertIsOn()
    }

    @Test
    fun cameraSettingsUseTheTrailingActionPosition() {
        val analysis = compose.onNodeWithContentDescription("导入照片").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot
        assertTrue(settings.left > analysis.right)
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("设置").assertExists()
    }

    @Test
    fun analysisModeIsConfiguredInSettingsAndSurvivesRecreation() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("快速").assertExists()
        compose.onNodeWithText("标准").assertDoesNotExist()
        compose.onNodeWithText("分析模式").performClick()
        compose.onNode(hasText("快速") and hasAnyAncestor(isDialog())).assertIsSelected()
        compose.onNodeWithText("标准").performClick()
        compose.onNodeWithText("标准").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("导入照片").performClick()
        compose.onNodeWithText("快速").assertDoesNotExist()
        compose.onNodeWithText("标准").assertDoesNotExist()
        compose.runOnIdle {
            val model = ViewModelProvider(compose.activity)[PhotoViewModel::class.java]
            assertEquals(AnalysisMode.Standard, model.state.value.mode)
        }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("标准").assertExists()
        compose.onNodeWithText("快速").assertDoesNotExist()
        compose.onNodeWithText("分析模式").performClick()
        compose.onNode(hasText("标准") and hasAnyAncestor(isDialog())).assertIsSelected()
    }

    @Test
    fun appearanceIsFirstAndThemeChoiceAppliesImmediatelyAndSurvivesRecreation() {
        compose.onNodeWithContentDescription("设置").performClick()
        val appearance = compose.onNodeWithText("外观").fetchSemanticsNode().boundsInRoot
        val analysis = compose.onNodeWithText("分析").fetchSemanticsNode().boundsInRoot
        assertTrue(appearance.bottom < analysis.top)
        val appearanceCard = compose.onNodeWithTag("settings-group-外观").fetchSemanticsNode().boundsInRoot
        val captureCard = compose.onNodeWithTag("settings-group-拍摄").fetchSemanticsNode().boundsInRoot
        val analysisCard = compose.onNodeWithTag("settings-group-分析").fetchSemanticsNode().boundsInRoot
        assertTrue(appearance.bottom < appearanceCard.top)
        assertTrue(appearanceCard.bottom < captureCard.top)
        assertTrue(captureCard.bottom < analysis.top && analysis.bottom < analysisCard.top)
        compose.onNodeWithText("跟随系统").assertExists()
        compose.onNodeWithText("主题").performClick()
        compose.onNode(hasText("跟随系统") and hasAnyAncestor(isDialog())).assertIsSelected()
        compose.onNodeWithText("深色").performClick()
        compose.onNodeWithText("深色").assertExists()
        compose.runOnIdle { assertTrue(!usesLightStatusBars()) }
        compose.onNodeWithText("主题").performClick()
        compose.onNodeWithText("浅色").performClick()
        compose.runOnIdle { assertTrue(usesLightStatusBars()) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("浅色").assertExists()
        compose.onNodeWithText("主题").performClick()
        compose.onNode(hasText("浅色") and hasAnyAncestor(isDialog())).assertIsSelected()
    }

    private fun usesLightStatusBars(): Boolean =
        WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView)
            .isAppearanceLightStatusBars

    @Test
    @Config(sdk = [31])
    fun dynamicColorIsOptInUsesTheSystemPaletteAndSurvivesRecreation() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("动态配色").assertIsOff()
        compose.onNodeWithText("显示评分").performClick()
        val systemPrimary = compose.activity.getColor(android.R.color.system_accent1_600)
        compose.onNodeWithText("动态配色").performClick().assertIsOn()
        assertTrue(renderContains(systemPrimary))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("动态配色").assertIsOn().performClick().assertIsOff()
        assertTrue(!renderContains(systemPrimary))
    }

    private fun renderContains(color: Int): Boolean {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return (0 until bitmap.width).any { x ->
            (0 until bitmap.height).any { y -> bitmap.getPixel(x, y) == color }
        }
    }

    @Test
    fun modelScoresAreOptInAndTheChoiceSurvivesRecreation() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("仅用于比较同一张照片的不同裁剪").assertExists()
        compose.onNodeWithText("显示评分").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("导入照片").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("显示评分").assertIsOn()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("显示评分").assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("选择照片").assertExists()
    }

    @Test
    fun analysisToolbarAndSystemBackBothReturnToTheCamera() {
        compose.onNodeWithContentDescription("导入照片").performClick()
        compose.onNodeWithText("选择照片").assertExists()
        compose.onNodeWithContentDescription("返回拍摄").performClick()
        compose.onNodeWithContentDescription("导入照片").assertExists()
        compose.onNodeWithContentDescription("导入照片").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("导入照片").assertExists()
        compose.onNodeWithText("选择照片").assertDoesNotExist()
    }

    @Test
    fun launchAndCancelledPhotoSelectionKeepEditingActionsHidden() {
        compose.onNodeWithContentDescription("导入照片").performClick()
        compose.onNodeWithText("选择照片").assertExists()
        compose.onNodeWithContentDescription("分析构图").assertDoesNotExist()
        compose.onNodeWithText("选择照片").performClick()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(request)
        assertEquals("image/*", request.intent.type)
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(request.intent, Activity.RESULT_CANCELED, null) }
        compose.onNodeWithContentDescription("分析构图").assertDoesNotExist()
        compose.onNodeWithText("选择照片").assertExists()
    }
}
