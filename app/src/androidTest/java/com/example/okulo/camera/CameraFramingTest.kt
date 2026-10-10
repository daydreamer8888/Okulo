package com.example.okulo.camera

import android.Manifest
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.filters.SdkSuppress
import androidx.test.rule.GrantPermissionRule
import com.example.okulo.composition.createCompositionAnalyzer
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

@SdkSuppress(minSdkVersion = 29)
class CameraFramingTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun liveRecommendationCanBeResizedRescoredRestoredAndCaptured() {
        val original = AtomicReference<Uri?>()
        val context = compose.activity
        lateinit var capture: CameraCapture
        val composition = CameraComposition(createCompositionAnalyzer(context))
        compose.runOnIdle {
            context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            capture = CameraCapture(context) { output, callback ->
                capture.controller.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            original.set(result.savedUri)
                            callback.onImageSaved(result)
                        }

                        override fun onError(exception: ImageCaptureException) = callback.onError(exception)
                    }
                )
            }
        }
        try {
            compose.setContent {
                OkuloTheme(darkTheme = true) {
                    CameraScreen(capture, composition, onImportPhoto = {}, showScores = true)
                }
            }
            compose.waitUntil(60_000) { compose.runOnIdle { capture.state.ready } }
            compose.onNodeWithContentDescription("推荐构图").performClick()
            awaitScore(composition)
            compose.onNodeWithTag("camera-crop").assertExists()
            compose.onNodeWithTag("composition-scores").assertExists()
            assertPreviewStreaming()
            compose.onNodeWithContentDescription("恢复推荐").assertIsNotEnabled()
            resizeRightEdge(composition)
            awaitScore(composition)
            compose.onNodeWithContentDescription("恢复推荐").assertIsEnabled().performClick()
            compose.onNodeWithContentDescription("恢复推荐").assertIsNotEnabled()
            compose.runOnIdle {
                assertEquals(composition.state.value.recommendation!!.crop, composition.state.value.crop)
            }
            resizeRightEdge(composition)
            awaitScore(composition)
            val area = compose.runOnIdle { composition.state.value.crop!!.area }
            assertTrue("Edited framing must crop the source", area < 1f)
            compose.onNodeWithContentDescription("拍照").performClick()
            compose.waitUntil(30_000) {
                compose.runOnIdle { !capture.state.saving && capture.state.savedPhoto != null }
            }
            val source = checkNotNull(original.get())
            val cropped = compose.runOnIdle { checkNotNull(capture.state.savedPhoto) }
            assertNotEquals("Both the original and crop must be saved", source, cropped)
            val sourceSize = displaySize(source)
            val cropSize = displaySize(cropped)
            assertTrue("Portrait capture must retain orientation", sourceSize.second > sourceSize.first)
            val savedArea = cropSize.first.toDouble() * cropSize.second / (sourceSize.first.toDouble() * sourceSize.second)
            assertEquals("Saved crop must match edited framing", area.toDouble(), savedArea, 0.02)
            assertPreviewStreaming()
            compose.onNodeWithContentDescription("关闭推荐").performClick()
            compose.onNodeWithTag("camera-crop").assertDoesNotExist()
            assertPreviewStreaming()
        } finally {
            compose.runOnIdle {
                listOfNotNull(original.get(), capture.state.savedPhoto).distinct().forEach {
                    context.contentResolver.delete(it, null, null)
                }
                capture.controller.unbind()
                capture.close()
                composition.close()
            }
        }
    }

    private fun awaitScore(composition: CameraComposition) {
        compose.waitUntil(60_000) {
            val state = composition.state.value
            !state.busy && !state.scoring && (state.cropScore != null || state.error != null)
        }
        compose.runOnIdle {
            val state = composition.state.value
            assertEquals(null, state.error)
            assertTrue(checkNotNull(state.originalScore).isFinite())
            assertTrue(checkNotNull(state.cropScore).isFinite())
        }
        compose.onNodeWithContentDescription("正在更新裁剪评分").assertDoesNotExist()
    }

    private fun resizeRightEdge(composition: CameraComposition) {
        val before = compose.runOnIdle { checkNotNull(composition.state.value.crop) }
        compose.onNodeWithTag("camera-crop").performTouchInput {
            val start = Offset(width * before.right, height * (before.top + before.bottom) / 2f)
            swipe(start, start - Offset(width * 0.08f, 0f), durationMillis = 300)
        }
        compose.runOnIdle {
            val after = checkNotNull(composition.state.value.crop)
            assertTrue("Dragging an edge must resize the crop", after.width < before.width)
            assertTrue(composition.state.value.canRestore)
        }
    }

    private fun assertPreviewStreaming() {
        onView(withContentDescription("后置主摄实时预览")).check { view, error ->
            if (error != null) throw error
            assertEquals(PreviewView.StreamState.STREAMING, (view as PreviewView).previewStreamState.value)
        }
    }

    private fun displaySize(uri: Uri): Pair<Int, Int> {
        val resolver = compose.activity.contentResolver
        val rotation = resolver.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        return resolver.openInputStream(uri)!!.use {
            val bitmap = checkNotNull(BitmapFactory.decodeStream(it))
            try {
                if (rotation % 180 == 0) bitmap.width to bitmap.height else bitmap.height to bitmap.width
            } finally {
                bitmap.recycle()
            }
        }
    }
}
