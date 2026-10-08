package com.example.okulo.photo

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.okulo.MainActivity
import com.example.okulo.composition.CropAspect
import com.example.okulo.composition.CropBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class AspectRecommendationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun selectedAndManuallyReshapedAspectsDriveRealRecommendations() {
        val source = File(compose.activity.cacheDir, "aspect-recommendation-test.png")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.context.assets.open("scene.png").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        lateinit var model: PhotoViewModel
        try {
            compose.onNodeWithContentDescription("分析照片").performClick()
            compose.runOnIdle {
                model = ViewModelProvider(compose.activity)[PhotoViewModel::class.java]
                model.selectPhoto(Uri.fromFile(source))
            }
            awaitIdle(model)
            compose.onNodeWithText("16:9").performScrollTo().performClick()
            compose.onNodeWithContentDescription("分析构图").performClick()
            awaitIdle(model)
            val wide = checkNotNull(model.state.value.displayedCrop) { model.state.value.error.orEmpty() }
            val photo = checkNotNull(model.state.value.photo)
            assertEquals(16f / 9f, wide.width * photo.width / (wide.height * photo.height), 1e-5f)
            compose.runOnIdle {
                model.updateCrop(
                    CropBox(
                        wide.left + wide.width * 0.1f,
                        wide.top + wide.height * 0.1f,
                        wide.right - wide.width * 0.1f,
                        wide.bottom - wide.height * 0.1f
                    )
                )
            }
            compose.onNodeWithContentDescription("恢复推荐").performClick()
            assertEquals(CropAspect.Wide, model.state.value.aspect)
            assertEquals(wide, model.state.value.displayedCrop)
            compose.onNodeWithText("自由").performScrollTo().performClick()
            compose.onNodeWithContentDescription("分析构图").performClick()
            awaitIdle(model)
            assertTrue(checkNotNull(model.state.value.result).candidateCount <= 250)
            compose.runOnIdle { model.updateCrop(CropBox(0.2f, 0.2f, 0.8f, 0.8f)) }
            compose.onNodeWithTag("crop-editor").performScrollTo().performTouchInput {
                swipe(Offset(width * 0.8f, height * 0.8f), Offset(width * 0.6f, height * 0.8f))
            }
            awaitIdle(model)
            val manual = checkNotNull(model.state.value.manualCrop)
            assertEquals(2f / 3f, manual.width / manual.height, 0.02f)
            assertTrue(checkNotNull(model.state.value.manualScore).isFinite())
            compose.onNodeWithContentDescription("分析构图").performClick()
            awaitIdle(model)
            assertNull(model.state.value.manualCrop)
            val recommended = checkNotNull(model.state.value.result)
            assertEquals(manual.width / manual.height, recommended.crop.width / recommended.crop.height, 1e-5f)
            assertTrue(recommended.cropScore.isFinite())
        } finally {
            source.delete()
        }
    }

    private fun awaitIdle(model: PhotoViewModel) {
        compose.waitUntil(45_000) { !model.state.value.busy && !model.state.value.scoring }
        compose.waitForIdle()
        assertNull(model.state.value.error)
    }
}
