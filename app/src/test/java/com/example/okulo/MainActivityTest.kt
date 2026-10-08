package com.example.okulo

import android.app.Activity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun cameraSettingsUseTheTrailingActionPosition() {
        val analysis = compose.onNodeWithContentDescription("分析照片").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot
        assertTrue(settings.left > analysis.right)
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("设置").assertExists()
    }

    @Test
    fun analysisModeIsConfiguredInSettingsAndSurvivesRecreation() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("快速分析").assertIsSelected()
        compose.onNodeWithText("标准分析").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("分析照片").performClick()
        compose.onNodeWithText("快速分析").assertDoesNotExist()
        compose.onNodeWithText("标准分析").assertDoesNotExist()
        compose.runOnIdle {
            val model = ViewModelProvider(compose.activity)[PhotoViewModel::class.java]
            assertEquals(AnalysisMode.Standard, model.state.value.mode)
        }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("标准分析").assertIsSelected()
    }

    @Test
    fun modelScoresAreOptInAndTheChoiceSurvivesRecreation() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("查看模型评分").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("分析照片").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("查看模型评分").assertIsOn()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("查看模型评分").assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("选择照片").assertExists()
    }

    @Test
    fun analysisToolbarAndSystemBackBothReturnToTheCamera() {
        compose.onNodeWithContentDescription("分析照片").performClick()
        compose.onNodeWithText("选择照片").assertExists()
        compose.onNodeWithContentDescription("返回拍摄").performClick()
        compose.onNodeWithContentDescription("分析照片").assertExists()
        compose.onNodeWithContentDescription("分析照片").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("分析照片").assertExists()
        compose.onNodeWithText("选择照片").assertDoesNotExist()
    }

    @Test
    fun launchAndCancelledPhotoSelectionKeepAnalysisDisabled() {
        compose.onNodeWithContentDescription("分析照片").performClick()
        compose.onNodeWithText("选择照片").assertExists()
        compose.onNodeWithContentDescription("分析构图").assertIsNotEnabled()
        compose.onNodeWithText("选择照片").performClick()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(request)
        assertEquals("image/*", request.intent.type)
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(request.intent, Activity.RESULT_CANCELED, null) }
        compose.onNodeWithContentDescription("分析构图").assertIsNotEnabled()
        compose.onNodeWithText("选择照片").assertExists()
    }
}
