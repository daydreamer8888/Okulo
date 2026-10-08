package com.example.okulo.camera

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
class CameraNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun photoEntryUsesAnAccessibleIconWithALongPressHint() {
        var entries = 0
        compose.setContent { OkuloTheme { CameraLayout({ entries++ }) {} } }
        compose.onNodeWithText("分析照片").assertDoesNotExist()
        val entry = compose.onNodeWithContentDescription("分析照片")
        entry.performTouchInput { longClick() }
        compose.onNodeWithText("分析照片").assertExists()
        assertEquals(0, entries)
        entry.performClick()
        assertEquals(1, entries)
    }
}
