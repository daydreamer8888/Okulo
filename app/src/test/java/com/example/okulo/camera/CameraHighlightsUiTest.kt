package com.example.okulo.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
class CameraHighlightsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun zebraOnlyCoversBrightRegionsAndClearsWhenTheFrameDarkensWithoutBlockingFocus() {
        val frame = mutableStateOf(LumaFrame(0))
        var taps = 0
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            Box(
                Modifier.size(240.dp, 320.dp).background(Color(0xff204060)).testTag("preview")
                    .clickable { taps++ }
            ) {
                CameraHighlightOverlay(readCameraHighlights(frame.value))
            }
        }
        val preview = compose.onNodeWithTag("preview")
        val bounds = preview.fetchSemanticsNode().boundsInRoot
        val overlay = compose.onNodeWithContentDescription("过曝区域", useUnmergedTree = true)
        overlay.assertExists()
        val overlayBounds = overlay.fetchSemanticsNode().boundsInRoot
        assertEquals(bounds, overlayBounds)
        assertQuadrants(view, expected = 0)
        preview.performTouchInput { click(center) }
        assertEquals(1, taps)
        compose.runOnIdle { frame.value = LumaFrame(90) }
        assertQuadrants(view, expected = 1)
        compose.runOnIdle { frame.value = LumaFrame(0, peak = 234) }
        overlay.assertDoesNotExist()
        assertQuadrants(view, expected = -1)
        assertEquals(bounds, preview.fetchSemanticsNode().boundsInRoot)
    }

    private fun countMarked(image: Bitmap, bounds: Rect, quadrant: Int): Int {
        val left = bounds.left.toInt() + (quadrant % 2) * bounds.width.toInt() / 2
        val top = bounds.top.toInt() + (quadrant / 2) * bounds.height.toInt() / 2
        var marked = 0
        for (y in top + 8 until top + bounds.height.toInt() / 2 - 8) {
            for (x in left + 8 until left + bounds.width.toInt() / 2 - 8) {
                if (image.getPixel(x, y) != 0xff204060.toInt()) marked++
            }
        }
        return marked
    }

    private fun assertQuadrants(view: View, expected: Int) {
        val bounds = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(image))
                for (quadrant in 0..3) {
                    val marked = countMarked(image, bounds, quadrant)
                    if (quadrant == expected) {
                        assertTrue("Bright region needs visible stripes", marked > 30)
                    } else {
                        assertEquals("Stripes leaked into dark quadrant $quadrant", 0, marked)
                    }
                }
            } finally {
                image.recycle()
            }
        }
    }
}
