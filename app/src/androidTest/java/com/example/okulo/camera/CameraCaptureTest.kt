package com.example.okulo.camera

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.view.PreviewView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.exifinterface.media.ExifInterface
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.okulo.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

@SdkSuppress(minSdkVersion = 29)
class CameraCaptureTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    )
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun shutterPublishesAViewableJpegAndKeepsTheViewfinderRunning() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val startedAt = System.currentTimeMillis()
        var captured: Uri? = null
        val viewIntent = AtomicReference<Intent?>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != MediaStore.ACTION_REVIEW) return null
                viewIntent.set(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        try {
            val shutter = compose.onNodeWithContentDescription("拍照")
            val photo = compose.onNodeWithContentDescription("查看最新照片")
            photo.assertExists()
            shutter.assertExists()
            compose.waitUntil(60_000) { !shutter.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
            shutter.performClick()
            compose.waitUntil(30_000) { !shutter.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("查看最新照片").fetchSemanticsNodes().any {
                    !it.config.contains(SemanticsProperties.Disabled)
                }
            }
            photo.performClick()
            compose.waitUntil(5_000) { viewIntent.get() != null }
            val intent = checkNotNull(viewIntent.get())
            assertEquals(MediaStore.ACTION_REVIEW, intent.action)
            assertEquals("image/jpeg", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val uri = checkNotNull(intent.data)
            val resolver = instrumentation.targetContext.contentResolver
            val columns = arrayOf(
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.IS_PENDING,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.OWNER_PACKAGE_NAME,
                MediaStore.Images.Media.DATE_TAKEN
            )
            resolver.query(uri, columns, null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("image/jpeg", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals("DCIM/Camera/", cursor.getString(2).trimEnd('/') + "/")
                assertEquals(instrumentation.targetContext.packageName, cursor.getString(3))
                assertTrue(cursor.getLong(4) >= startedAt)
            }
            captured = uri
            resolver.openInputStream(uri)!!.use { input ->
                assertEquals(0xff, input.read())
                assertEquals(0xd8, input.read())
                assertEquals(0xff, input.read())
            }
            val rotation = resolver.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
            resolver.openInputStream(uri)!!.use { input ->
                val bitmap = checkNotNull(BitmapFactory.decodeStream(input))
                val width = if (rotation % 180 == 0) bitmap.width else bitmap.height
                val height = if (rotation % 180 == 0) bitmap.height else bitmap.width
                assertTrue("Portrait capture must retain its display orientation", height > width)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("Saved photo must contain camera pixels", pixels.any { it != pixels[0] })
                bitmap.recycle()
            }
            onView(withContentDescription("后置主摄实时预览")).check { view, error ->
                if (error != null) throw error
                assertEquals(PreviewView.StreamState.STREAMING, (view as PreviewView).previewStreamState.value)
            }
        } finally {
            captured?.let { instrumentation.targetContext.contentResolver.delete(it, null, null) }
            instrumentation.removeMonitor(monitor)
        }
    }
}
