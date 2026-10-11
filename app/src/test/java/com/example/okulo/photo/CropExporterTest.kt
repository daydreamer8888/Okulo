package com.example.okulo.photo

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.example.okulo.composition.CropBox
import com.example.okulo.image.CropExporter
import com.example.okulo.image.PhotoOperation
import com.example.okulo.image.PhotoTransform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropExporterTest {
    @Test
    fun exportUsesFullResolutionAndCurrentOrientationWithoutChangingTheSource() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "crop-source.png")
        val bitmap = Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        Canvas(bitmap).drawRect(0f, 0f, 1500f, 2000f, Paint().apply { color = Color.RED })
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val original = source.readBytes()
        val gallery = ExportGallery(File(context.cacheDir, "crop-output.jpg"))
        gallery.attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, gallery)
        val transform = PhotoTransform().followedBy(PhotoOperation.RotateClockwise)
            .followedBy(PhotoOperation.FlipVertical)
        val uri = CropExporter(context).save(Uri.fromFile(source), transform, CropBox(0.25f, 0.1f, 0.75f, 0.9f))
        val output = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        assertEquals(1000, output.width)
        assertEquals(2400, output.height)
        assertTrue(Color.blue(output.getPixel(500, 600)) > 200)
        assertTrue(Color.red(output.getPixel(500, 1800)) > 200)
        assertEquals("Pictures/Okulo", gallery.values.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertTrue(gallery.values.getAsString(MediaStore.Images.Media.DISPLAY_NAME).startsWith("Okulo_crop_"))
        assertEquals("image/jpeg", gallery.values.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertEquals(0, gallery.values.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertArrayEquals(original, source.readBytes())
        output.recycle()
    }

    @Test
    @Config(sdk = [28])
    @Suppress("DEPRECATION") // Verify the gallery contract used on Android 8 and 9.
    fun legacyExportUsesAFileLocationWithoutPendingPublicationFields() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "legacy-crop-source.png")
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val gallery = ExportGallery(File(context.cacheDir, "legacy-crop-output.jpg"))
        gallery.attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, gallery)
        CropExporter(context).save(Uri.fromFile(source), PhotoTransform(), CropBox.FullFrame)
        val destination = File(gallery.values.getAsString(MediaStore.Images.Media.DATA))
        assertEquals("Okulo", destination.parentFile?.name)
        assertEquals(gallery.values.getAsString(MediaStore.Images.Media.DISPLAY_NAME), destination.name)
        assertTrue(destination.name.startsWith("Okulo_crop_") && destination.name.endsWith(".jpg"))
        assertEquals("image/jpeg", gallery.values.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertFalse(gallery.values.containsKey(MediaStore.Images.Media.IS_PENDING))
        assertFalse(gallery.values.containsKey(MediaStore.Images.Media.RELATIVE_PATH))
        assertTrue(gallery.file.exists())
    }

    @Test
    fun failedOutputRemovesTheIncompleteGalleryEntry() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "failure-source.png")
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val gallery = ExportGallery(File(context.cacheDir, "failed-output.jpg"), failWrites = true)
        gallery.attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, gallery)
        var failed = false
        try {
            CropExporter(context).save(Uri.fromFile(source), PhotoTransform(), CropBox.FullFrame)
        } catch (_: IOException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(1, gallery.deletions)
        assertTrue(!gallery.file.exists())
    }
}

internal class ExportGallery(val file: File, private val failWrites: Boolean = false) : ContentProvider() {
    val values = ContentValues()
    var deletions = 0
    var insertions = 0
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/jpeg"
    override fun insert(uri: Uri, incoming: ContentValues?): Uri {
        insertions++
        values.putAll(checkNotNull(incoming))
        return Uri.parse("content://media/external/images/media/1")
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (failWrites && mode.contains('w')) throw FileNotFoundException("Output unavailable")
        val access = if (mode.contains('w')) {
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE
        } else {
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, access)
    }
    override fun update(uri: Uri, incoming: ContentValues?, selection: String?, args: Array<out String>?): Int {
        values.putAll(checkNotNull(incoming))
        return 1
    }
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
        deletions++
        file.delete()
        return 1
    }
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        args: Array<out String>?,
        sortOrder: String?
    ): Cursor = MatrixCursor(projection ?: emptyArray())
}
