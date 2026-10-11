package com.example.okulo.camera

import android.annotation.SuppressLint
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Looper
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.exifinterface.media.ExifInterface
import com.example.okulo.composition.CropBox
import com.example.okulo.image.CropWriter
import com.example.okulo.photo.ExportGallery
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraFrozenCaptureTest {
    @Test
    @SuppressLint("RestrictedApi")
    fun savingTheFrozenCropUsesTheSameOrientedPhotoAfterTheCameraSceneChanges() {
        val context = RuntimeEnvironment.getApplication()
        val gallery = ExportGallery(File(context.cacheDir, "frozen-export.jpg"))
        gallery.attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, gallery)
        var shots = 0
        lateinit var source: File
        var photo: FrozenCameraPhoto? = null
        CameraCapture(context) { output, callback ->
            shots++
            source = checkNotNull(output.file)
            val image = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            source.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            image.recycle()
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            callback.onImageSaved(ImageCapture.OutputFileResults(null))
        }.use { capture ->
            capture.previewReady(true)
            capture.freeze { photo = it }
            await { photo != null }
            val frozen = checkNotNull(photo)
            assertEquals(0, gallery.insertions)
            assertEquals(200, frozen.image.width)
            assertEquals(300, frozen.image.height)
            capture.previewReady(false)
            capture.saveFrozen(frozen, CropBox(0f, 0f, 1f, 0.5f), keepOriginal = false)
            frozen.close()
            await { !capture.state.saving }
            assertEquals(1, shots)
            assertEquals(1, gallery.insertions)
            val exported = context.contentResolver.openInputStream(capture.state.savedPhoto!!).use {
                BitmapFactory.decodeStream(it)
            }
            assertEquals(200, exported.width)
            assertEquals(150, exported.height)
            assertTrue(Color.red(exported.getPixel(100, 75)) > 200)
            exported.recycle()
            assertFalse(source.exists())
            assertTrue(frozen.image.isRecycled)
        }
    }

    @Test
    fun keepingTheOriginalPreservesCapturedBytesEvenWhenCropExportFails() {
        val context = RuntimeEnvironment.getApplication()
        val gallery = ExportGallery(File(context.cacheDir, "frozen-original.jpg"))
        gallery.attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, gallery)
        val source = File.createTempFile("frozen-original-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        val bytes = source.readBytes()
        val frozen = FrozenCameraPhoto(bitmap, source)
        val unavailable = CropWriter { _, _, _ -> throw IOException("Gallery crop write failed") }
        CameraCapture(context, writer = unavailable).use { capture ->
            capture.saveFrozen(frozen, CropBox(0f, 0f, 0.5f, 1f), keepOriginal = true)
            frozen.close()
            await { !capture.state.saving }
            assertEquals("裁剪保存失败，原图已保留", capture.state.message)
            assertTrue(capture.state.savedPhoto != null)
            assertEquals(1, gallery.insertions)
            assertArrayEquals(bytes, gallery.file.readBytes())
            await { !source.exists() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.nanoTime() < deadline) { "Frozen capture timed out" }
            Thread.yield()
        }
    }
}
