package com.example.okulo.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun cameraActionsMirrorTheRecentPhotoAroundTheShutterWithoutSubjectSelection() {
        compose.setContent {
            OkuloTheme {
                Box(Modifier.size(320.dp, 600.dp).testTag("camera-layout")) {
                    CameraLayout({}, capture = CaptureUiState(ready = true)) {}
                }
            }
        }
        compose.onNodeWithContentDescription("框选主体").assertDoesNotExist()
        val screen = compose.onNodeWithTag("camera-layout").fetchSemanticsNode().boundsInRoot
        val recent = compose.onNodeWithContentDescription("查看最新照片").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
        val recommend = compose.onNodeWithContentDescription("推荐构图").fetchSemanticsNode().boundsInRoot
        assertEquals(screen.center.x, shutter.center.x, 1f)
        assertEquals((screen.left + shutter.left) / 2f, recent.center.x, 1f)
        assertEquals(shutter.center.x - recent.center.x, recommend.center.x - shutter.center.x, 1f)
        assertEquals(recent.center.y, recommend.center.y, 1f)
        assertTrue(recent.right <= shutter.left && shutter.right <= recommend.left)
    }

    @Test
    fun photoImportAndSettingsShareATrailingToolbarGroup() {
        var imports = 0
        var settings = 0
        compose.setContent {
            OkuloTheme { CameraLayout({ imports++ }, onSettings = { settings++ }) {} }
        }
        val entry = compose.onNodeWithContentDescription("导入照片")
        val gear = compose.onNodeWithContentDescription("设置")
        val entryBounds = entry.fetchSemanticsNode().boundsInRoot
        val gearBounds = gear.fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(entryBounds.center.x > screen.center.x)
        assertTrue(entryBounds.right <= gearBounds.left)
        assertEquals(entryBounds.center.y, gearBounds.center.y, 1f)
        entry.performClick()
        gear.performClick()
        assertEquals(1, imports)
        assertEquals(1, settings)
    }

    @Test
    fun photoEntryUsesAnAccessibleIconWithALongPressHint() {
        var entries = 0
        compose.setContent { OkuloTheme { CameraLayout({ entries++ }) {} } }
        compose.onNodeWithText("导入照片").assertDoesNotExist()
        val entry = compose.onNodeWithContentDescription("导入照片")
        entry.performTouchInput { longClick() }
        compose.onNodeWithText("导入照片").assertExists()
        assertEquals(0, entries)
        entry.performClick()
        assertEquals(1, entries)
    }
}
