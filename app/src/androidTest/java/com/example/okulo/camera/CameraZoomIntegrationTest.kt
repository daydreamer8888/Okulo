package com.example.okulo.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ZoomState
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.filters.SdkSuppress
import androidx.test.rule.GrantPermissionRule
import com.example.okulo.composition.createCompositionAnalyzer
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.text.NumberFormat
import java.util.concurrent.atomic.AtomicReference

@SdkSuppress(minSdkVersion = 29)
class CameraZoomIntegrationTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun zoomControlsFollowTheCameraAndFramedCaptureUsesTheNewViewfinder() {
        lateinit var capture: CameraCapture
        lateinit var composition: CameraComposition
        val sourceSize = AtomicReference<Pair<Int, Int>>()
        compose.runOnIdle {
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            capture = recordingCapture(sourceSize)
            composition = CameraComposition(createCompositionAnalyzer(compose.activity))
        }
        try {
            compose.setContent {
                OkuloTheme(darkTheme = true) {
                    CameraScreen(capture, composition, {}, keepOriginal = false)
                }
            }
            compose.waitUntil(60_000) {
                compose.runOnIdle { capture.state.ready && capture.controller.zoomState.value != null }
            }
            val initial = zoomState(capture)
            assumeTrue("This camera does not support zoom", initial.maxZoomRatio > initial.minZoomRatio)
            val viewfinder = compose.onNodeWithContentDescription("取景区域").fetchSemanticsNode().boundsInRoot
            val shutter = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
            openSlider(initial.zoomRatio)
            compose.onNodeWithContentDescription("变焦倍率").performSemanticsAction(SemanticsActions.SetProgress) {
                it(0.35f)
            }
            compose.waitUntil(10_000) { kotlin.math.abs(zoomState(capture).linearZoom - 0.35f) < 0.001f }
            val sliderRatio = zoomState(capture).zoomRatio
            compose.onNodeWithText(label(sliderRatio)).assertExists()
            assertEquals(viewfinder, compose.onNodeWithContentDescription("取景区域").fetchSemanticsNode().boundsInRoot)
            assertEquals(shutter, compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot)
            recommend(composition)
            pinchOut()
            compose.waitUntil(10_000) {
                zoomState(capture).zoomRatio > sliderRatio && compose.runOnIdle { composition.state.value.crop == null }
            }
            compose.onNodeWithTag("camera-crop").assertDoesNotExist()
            val pinched = zoomState(capture)
            openSlider(pinched.zoomRatio)
            compose.onNodeWithText(label(pinched.zoomRatio)).assertExists()
            compose.onNodeWithContentDescription("收起变焦调节").performClick()
            recommend(composition)
            captureAndCheck(capture, composition, sourceSize)
            assertStreaming()
        } finally {
            compose.runOnIdle {
                capture.state.savedPhoto?.let { compose.activity.contentResolver.delete(it, null, null) }
                capture.controller.unbind()
                composition.close()
                capture.close()
            }
        }
    }

    private fun zoomState(capture: CameraCapture): ZoomState = compose.runOnIdle {
        checkNotNull(capture.controller.zoomState.value)
    }

    private fun openSlider(ratio: Float) {
        if (compose.onAllNodesWithContentDescription("变焦倍率").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("变焦，当前 ${label(ratio)}").performClick()
        }
    }

    private fun recommend(composition: CameraComposition) {
        val action = compose.onNodeWithContentDescription("推荐构图")
        compose.waitUntil(10_000) { !action.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
        action.performClick()
        compose.waitUntil(60_000) {
            compose.runOnIdle {
                val state = composition.state.value
                check(state.error == null) { state.error!! }
                !state.busy && state.crop != null
            }
        }
        compose.onNodeWithTag("camera-crop").assertExists()
    }

    private fun pinchOut() {
        compose.onNodeWithContentDescription("取景区域").performTouchInput {
            down(0, Offset(width * 0.35f, height * 0.4f))
            down(1, Offset(width * 0.65f, height * 0.4f))
            moveTo(0, Offset(width * 0.25f, height * 0.4f))
            moveTo(1, Offset(width * 0.75f, height * 0.4f))
            up(0)
            up(1)
        }
    }

    private fun captureAndCheck(
        capture: CameraCapture,
        composition: CameraComposition,
        sourceSize: AtomicReference<Pair<Int, Int>>
    ) {
        val crop = compose.runOnIdle { checkNotNull(composition.state.value.crop) }
        compose.onNodeWithContentDescription("拍照").performClick()
        compose.waitUntil(30_000) {
            compose.runOnIdle { !capture.state.saving && capture.state.savedPhoto != null }
        }
        val (width, height) = checkNotNull(sourceSize.get())
        val saved = compose.runOnIdle { checkNotNull(capture.state.savedPhoto) }
        assertEquals("image/jpeg", compose.activity.contentResolver.getType(saved))
        compose.activity.contentResolver.openInputStream(saved)!!.use {
            val bitmap = checkNotNull(BitmapFactory.decodeStream(it))
            try {
                assertEquals(width * crop.width.toDouble(), bitmap.width.toDouble(), 2.0)
                assertEquals(height * crop.height.toDouble(), bitmap.height.toDouble(), 2.0)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("Saved crop must contain camera pixels", pixels.any { pixel -> pixel != pixels[0] })
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun assertStreaming() {
        onView(withContentDescription("后置主摄实时预览")).check { view, error ->
            if (error != null) throw error
            assertEquals(PreviewView.StreamState.STREAMING, (view as PreviewView).previewStreamState.value)
        }
    }

    @SuppressLint("RestrictedApi") // Record this test's private capture at the camera output boundary.
    private fun recordingCapture(sourceSize: AtomicReference<Pair<Int, Int>>): CameraCapture {
        lateinit var capture: CameraCapture
        capture = CameraCapture(compose.activity) { output, callback ->
            capture.controller.takePicture(
                output,
                ContextCompat.getMainExecutor(compose.activity),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        sourceSize.set(displaySize(checkNotNull(output.file)))
                        callback.onImageSaved(result)
                    }

                    override fun onError(exception: ImageCaptureException) = callback.onError(exception)
                }
            )
        }
        return capture
    }

    private fun displaySize(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return if (ExifInterface(file).rotationDegrees % 180 == 0) {
            options.outWidth to options.outHeight
        } else {
            options.outHeight to options.outWidth
        }
    }

    private fun label(ratio: Float): String = NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = 1
        isGroupingUsed = false
    }.format(ratio) + "×"
}
