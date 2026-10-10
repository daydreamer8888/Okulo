package com.example.okulo.photo

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.okulo.ui.theme.OkuloTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoSaveFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun eachSaveShowsFeedbackBelowTheToolbarWithoutReplacingTheTitleOrMovingActions() {
        val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val photo = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
        compose.setContent {
            OkuloTheme {
                PhotoScreen(
                    PhotoState(photo = photo, result = recommendation()),
                    {},
                    {},
                    {},
                    onSave = { assertTrue(events.tryEmit("已保存到相册")) },
                    saveEvents = events
                )
            }
        }
        val save = compose.onNodeWithContentDescription("保存")
        val saveBounds = save.fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithContentDescription("返回拍摄").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot
        val imageBounds = compose.onNodeWithContentDescription("可调整裁剪框的原图").fetchSemanticsNode().boundsInRoot
        save.performClick()
        compose.onNodeWithText("裁剪").assertExists()
        val notice = compose.onNodeWithText("已保存到相册").fetchSemanticsNode().boundsInRoot
        assertTrue(notice.top > maxOf(back.bottom, settings.bottom))
        assertEquals(
            imageBounds,
            compose.onNodeWithContentDescription("可调整裁剪框的原图").fetchSemanticsNode().boundsInRoot
        )
        assertTrue(notice.bottom < saveBounds.top)
        assertEquals(saveBounds, save.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_200)
        save.assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithText("已保存到相册").assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("已保存到相册").assertDoesNotExist()
        compose.onNodeWithText("裁剪").assertExists()
        assertEquals(saveBounds, save.fetchSemanticsNode().boundsInRoot)
    }
}
