package com.example.okulo.photo

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
            compose.onNodeWithContentDescription("导入照片").performClick()
            compose.runOnIdle {
                model = ViewModelProvider(compose.activity)[PhotoViewModel::class.java]
                model.selectPhoto(Uri.fromFile(source))
            }
            awaitIdle(model)
            compose.onNodeWithContentDescription("裁剪比例").assertIsDisplayed().performClick()
            compose.onNodeWithText("16:9").performClick()
            compose.runOnIdle { assertEquals(CropAspect.Wide, model.state.value.aspect) }
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
            compose.onNodeWithContentDescription("恢复").performClick()
            assertEquals(CropAspect.Wide, model.state.value.aspect)
            assertEquals(wide, model.state.value.displayedCrop)
            compose.onNodeWithContentDescription("裁剪比例").assertIsDisplayed().performClick()
            compose.onNodeWithText("自由").performClick()
            compose.runOnIdle { assertEquals(CropAspect.Free, model.state.value.aspect) }
            compose.onNodeWithContentDescription("分析构图").performClick()
            awaitIdle(model)
            val freeRecommendation = checkNotNull(model.state.value.result)
            assertTrue(freeRecommendation.candidateCount <= 600)
            compose.runOnIdle { model.updateCrop(CropBox(0.2f, 0.2f, 0.8f, 0.8f)) }
            compose.waitForIdle()
            compose.onNodeWithTag("crop-editor").assertIsDisplayed()
            compose.waitForIdle()
            compose.onNodeWithTag("crop-editor").performTouchInput {
                swipe(Offset(width * 0.8f, height * 0.5f), Offset(width * 0.6f, height * 0.5f))
            }
            awaitIdle(model)
            val manual = checkNotNull(model.state.value.manualCrop)
            assertEquals(2f / 3f, manual.width / manual.height, 0.02f)
            assertTrue(checkNotNull(model.state.value.manualScore).isFinite())
            compose.onNodeWithContentDescription("恢复").assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(freeRecommendation.crop, model.state.value.displayedCrop)
                assertNull(model.state.value.manualCrop)
                assertNull(model.state.value.freeRatio)
                model.updateCrop(manual)
            }
            compose.waitForIdle()
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
