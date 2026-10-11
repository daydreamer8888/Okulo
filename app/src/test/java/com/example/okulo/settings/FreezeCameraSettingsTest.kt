package com.example.okulo.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FreezeCameraSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun freezingStartsOnAndPersistsIndependentlyOfKeepingTheOriginal() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("okulo_settings", 0).edit().clear().commit()
        val settings = mutableStateOf(AppSettings(context))
        compose.setContent {
            OkuloTheme {
                SettingsScreen(
                    false,
                    {},
                    {},
                    keepOriginal = settings.value.keepCaptureOriginal,
                    onKeepOriginal = settings.value::setCaptureOriginal,
                    freezeComposition = settings.value.freezeComposition,
                    onFreezeComposition = settings.value::updateFreezeComposition
                )
            }
        }
        val freeze = compose.onNodeWithText("推荐构图时定格画面")
        val original = compose.onNodeWithText("同时保存原图")
        freeze.performScrollTo().assertIsOn()
        original.assertIsOn()
        assertTrue(freeze.fetchSemanticsNode().boundsInRoot.top < original.fetchSemanticsNode().boundsInRoot.top)
        freeze.performClick().assertIsOff()
        original.performClick().assertIsOff()
        compose.runOnIdle { settings.value = AppSettings(context) }
        freeze.assertIsOff()
        original.assertIsOff()
        freeze.performClick().assertIsOn()
        compose.runOnIdle { settings.value = AppSettings(context) }
        freeze.assertIsOn()
        original.assertIsOff()
    }
}
