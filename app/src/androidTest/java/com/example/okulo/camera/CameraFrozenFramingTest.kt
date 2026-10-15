package com.example.okulo.camera

import android.Manifest
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
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
import androidx.test.filters.SdkSuppress
import androidx.test.rule.GrantPermissionRule
import com.example.okulo.composition.createCompositionAnalyzer
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@SdkSuppress(minSdkVersion = 29)
class CameraFrozenFramingTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun freezingAllowsEditingAndSavingTheCapturedPhotoWithoutAnotherCameraShot() {
        val context = compose.activity
        val composition = CameraComposition(createCompositionAnalyzer(context))
        lateinit var capture: CameraCapture
        var shots = 0
        compose.runOnIdle {
            context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            capture = CameraCapture(context) { output, callback ->
                shots++
                capture.controller.takePicture(output, ContextCompat.getMainExecutor(context), callback)
            }
        }
        try {
            compose.setContent {
                OkuloTheme(darkTheme = true) {
                    CameraScreen(
                        capture, composition, onImportPhoto = {}, showScores = true,
                        keepOriginal = false, freezeComposition = true
                    )
                }
            }
            compose.waitUntil(60_000) { compose.runOnIdle { capture.state.ready } }
            compose.onNodeWithContentDescription("推荐构图").performClick()
            awaitScore(composition)
            val frozen = compose.runOnIdle { checkNotNull(composition.state.value.frozenPhoto) }
            val source = File(checkNotNull(frozen.uri.path))
            compose.onNodeWithContentDescription("定格画面").assertExists()
            compose.onNodeWithContentDescription("恢复").assertIsNotEnabled()
            val before = compose.runOnIdle { checkNotNull(composition.state.value.crop) }
            compose.onNodeWithTag("camera-crop").performTouchInput {
                val start = Offset(width * before.right, height * (before.top + before.bottom) / 2f)
                swipe(start, start - Offset(width * 0.08f, 0f), durationMillis = 300)
            }
            awaitScore(composition)
            compose.onNodeWithContentDescription("恢复").assertIsEnabled()
            val crop = compose.runOnIdle { checkNotNull(composition.state.value.crop) }
            assertTrue(crop.width < before.width)
            assertEquals(frozen, compose.runOnIdle { composition.state.value.frozenPhoto })
            assertEquals(1, shots)
            compose.onNodeWithContentDescription("拍照").performClick()
            compose.waitUntil(30_000) { compose.runOnIdle { !capture.state.saving && capture.state.savedPhoto != null } }
            assertEquals(1, shots)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, options)
            val rotated = ExifInterface(source).rotationDegrees % 180 != 0
            val sourceWidth = if (rotated) options.outHeight else options.outWidth
            val sourceHeight = if (rotated) options.outWidth else options.outHeight
            context.contentResolver.openInputStream(capture.state.savedPhoto!!)!!.use {
                val image = checkNotNull(BitmapFactory.decodeStream(it))
                try {
                    assertEquals(sourceWidth * crop.width.toDouble(), image.width.toDouble(), 2.0)
                    assertEquals(sourceHeight * crop.height.toDouble(), image.height.toDouble(), 2.0)
                } finally {
                    image.recycle()
                }
            }
            compose.onNodeWithContentDescription("返回取景").performClick()
            compose.onNodeWithContentDescription("定格画面").assertDoesNotExist()
            compose.waitUntil(10_000) { !source.exists() }
        } finally {
            compose.runOnIdle {
                capture.state.savedPhoto?.let { context.contentResolver.delete(it, null, null) }
                capture.controller.unbind()
                composition.close()
                capture.close()
            }
        }
    }

    private fun awaitScore(composition: CameraComposition) {
        compose.waitUntil(60_000) {
            val state = composition.state.value
            !state.busy && !state.scoring && (state.cropScore != null || state.error != null)
        }
        compose.runOnIdle {
            assertEquals(null, composition.state.value.error)
            assertTrue(checkNotNull(composition.state.value.cropScore).isFinite())
        }
    }
}
