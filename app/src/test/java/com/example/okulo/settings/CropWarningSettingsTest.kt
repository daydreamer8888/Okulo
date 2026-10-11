package com.example.okulo.settings

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CropBox
import com.example.okulo.photo.PhotoScreen
import com.example.okulo.photo.PhotoState
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropWarningSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun clearSettings() {
        RuntimeEnvironment.getApplication().getSharedPreferences("okulo_settings", 0).edit().clear().commit()
    }

    @Test
    fun confirmedSliderThresholdPersistsAndControlsTheCropWarning() {
        val context = RuntimeEnvironment.getApplication()
        val settings = mutableStateOf(AppSettings(context))
        val showingSettings = mutableStateOf(true)
        val photo = mutableStateOf(
            PhotoState(
                photo = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888),
                manualCrop = CropBox(0f, 0f, 1f, 0.3f)
            )
        )
        compose.setContent {
            OkuloTheme {
                if (showingSettings.value) {
                    SettingsScreen(
                        false,
                        {},
                        { showingSettings.value = false },
                        AnalysisMode.Fast,
                        {},
                        cropWarningPercent = settings.value.cropWarningPercent,
                        onCropWarning = settings.value::updateCropWarningPercent
                    )
                } else {
                    PhotoScreen(photo.value, {}, {}, {}, cropWarningPercent = settings.value.cropWarningPercent)
                }
            }
        }
        compose.onNodeWithText("小于原图的 20% 时提醒裁剪区域较小").performScrollTo().performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        setProgress(90f)
        compose.onAllNodes(androidx.compose.ui.test.hasText("小于原图的 90% 时提醒裁剪区域较小")).assertCountEquals(1)
        compose.onNodeWithText("取消").performScrollTo().performClick()
        compose.onNodeWithText("小于原图的 20% 时提醒裁剪区域较小").performScrollTo().performClick()
        setProgress(34f)
        compose.onNodeWithText("小于原图的 30% 时提醒裁剪区域较小").assertExists()
        compose.onNodeWithText("确定").performScrollTo().performClick()
        compose.onNodeWithText("小于原图的 30% 时提醒裁剪区域较小").assertExists()
        compose.runOnIdle { settings.value = AppSettings(context) }
        compose.onNodeWithText("小于原图的 30% 时提醒裁剪区域较小").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("裁剪区域较小").assertDoesNotExist()
        val bounds = compose.onNodeWithContentDescription("保存").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { photo.value = photo.value.copy(manualCrop = CropBox(0f, 0f, 1f, 0.29f)) }
        compose.onNodeWithText("裁剪区域较小").assertExists()
        compose.onNodeWithContentDescription("保存").assertIsEnabled()
        org.junit.Assert.assertEquals(
            bounds,
            compose.onNodeWithContentDescription("保存").fetchSemanticsNode().boundsInRoot
        )
    }

    private fun setProgress(value: Float) {
        compose.onNodeWithContentDescription("裁剪面积提醒阈值").performSemanticsAction(SemanticsActions.SetProgress) {
            it(value)
        }
    }
}
