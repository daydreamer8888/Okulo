package com.example.okulo.camera

import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.exifinterface.media.ExifInterface
import com.example.okulo.composition.CropBox
import com.example.okulo.photo.ExportGallery
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraCropCaptureTest {
    @Test
    fun shutterKeepsTheOriginalAndSavesTheSelectedCropWithCaptureOrientation() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "camera-original.jpg")
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        Canvas(bitmap).drawRect(0f, 0f, 150f, 200f, Paint().apply { color = Color.RED })
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val original = source.readBytes()
        val gallery = ExportGallery(File(context.cacheDir, "camera-crop.jpg"))
        gallery.attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, gallery)
        var shots = 0
        CameraCapture(context) { _, callback ->
            shots++
            callback.onImageSaved(ImageCapture.OutputFileResults(Uri.fromFile(source)))
        }.use { capture ->
            capture.previewReady(true)
            capture.takePhoto(CropBox(0f, 0.5f, 1f, 1f))
            assertTrue(capture.state.saving)
            capture.takePhoto(CropBox.FullFrame)
            await { !capture.state.saving }
            assertEquals(1, shots)
            assertEquals(1, gallery.insertions)
            val output = context.contentResolver.openInputStream(capture.state.savedPhoto!!).use {
                BitmapFactory.decodeStream(it)
            }
            assertEquals(200, output.width)
            assertEquals(150, output.height)
            assertTrue(Color.blue(output.getPixel(100, 75)) > 200)
            assertEquals("已保存到相册", capture.state.message)
            assertArrayEquals(original, source.readBytes())
            output.recycle()
            capture.takePhoto()
            assertEquals(2, shots)
            assertEquals(Uri.fromFile(source), capture.state.savedPhoto)
            assertEquals(1, gallery.insertions)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.nanoTime() < deadline) { "Crop capture timed out" }
            Thread.yield()
        }
    }
}
