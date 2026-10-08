package com.example.okulo

import android.app.Activity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun launchAndCancelledPhotoSelectionKeepAnalysisDisabled() {
        compose.onNodeWithText("导入照片").performClick()
        compose.onNodeWithText("Okulo").assertExists()
        compose.onNodeWithText("分析构图").assertIsNotEnabled()
        compose.onNodeWithText("选择照片").performClick()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(request)
        assertEquals("image/*", request.intent.type)
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(request.intent, Activity.RESULT_CANCELED, null) }
        compose.onNodeWithText("分析构图").assertIsNotEnabled()
        compose.onNodeWithText("选择照片").assertExists()
    }
}
