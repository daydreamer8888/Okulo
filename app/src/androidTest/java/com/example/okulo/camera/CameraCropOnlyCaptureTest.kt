package com.example.okulo.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.test.filters.SdkSuppress
import androidx.test.rule.GrantPermissionRule
import com.example.okulo.composition.CropBox
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@SdkSuppress(minSdkVersion = 29)
class CameraCropOnlyCaptureTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @SuppressLint("RestrictedApi") // Observe the requested camera destination at the platform boundary.
    fun capturePublishesOnlyTheCropAndReleasesThePrivateOriginal() {
        val context = compose.activity
        val temporary = AtomicReference<File?>()
        val original = AtomicReference<Uri?>()
        val sourceSize = AtomicReference<Pair<Int, Int>>()
        lateinit var capture: CameraCapture
        compose.runOnIdle {
            context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            capture = CameraCapture(context) { output, callback ->
                temporary.set(output.file)
                capture.controller.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            original.set(result.savedUri)
                            val file = checkNotNull(temporary.get())
                            sourceSize.set(displaySize(file))
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
                    CameraLayout(
                        onImportPhoto = {},
                        capture = capture.state,
                        onCapture = { capture.takePhoto(CropBox(0.25f, 0.25f, 0.75f, 0.75f), keepOriginal = false) }
                    ) {
                        CameraPreview(capture)
                    }
                }
            }
            compose.waitUntil(60_000) { compose.runOnIdle { capture.state.ready } }
            compose.onNodeWithContentDescription("拍照").performClick()
            compose.waitUntil(30_000) {
                compose.runOnIdle { !capture.state.saving && capture.state.savedPhoto != null } &&
                    temporary.get()?.exists() == false
            }
            val file = checkNotNull(temporary.get())
            assertEquals(context.cacheDir, file.parentFile)
            val saved = compose.runOnIdle { checkNotNull(capture.state.savedPhoto) }
            assertEquals("content", saved.scheme)
            assertEquals("image/jpeg", context.contentResolver.getType(saved))
            val size = checkNotNull(sourceSize.get())
            context.contentResolver.openInputStream(saved)!!.use {
                val bitmap = checkNotNull(BitmapFactory.decodeStream(it))
                try {
                    assertEquals(size.first / 2.0, bitmap.width.toDouble(), 2.0)
                    assertEquals(size.second / 2.0, bitmap.height.toDouble(), 2.0)
                    assertTrue(bitmap.height > bitmap.width)
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            compose.runOnIdle {
                listOfNotNull(original.get(), capture.state.savedPhoto).distinct()
                    .filter { it.scheme == "content" }.forEach { context.contentResolver.delete(it, null, null) }
                temporary.get()?.delete()
                capture.controller.unbind()
                capture.close()
            }
        }
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
}
