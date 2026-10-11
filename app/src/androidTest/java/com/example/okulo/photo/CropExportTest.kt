package com.example.okulo.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.okulo.composition.CropBox
import com.example.okulo.image.CropExporter
import com.example.okulo.image.PhotoOperation
import com.example.okulo.image.PhotoTransform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@SdkSuppress(minSdkVersion = 29)
class CropExportTest {
    @Test
    fun editedPhotoIsPublishedToTheGalleryAtTheRequestedSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "crop-export-test.png")
        var saved: Uri? = null
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val original = source.readBytes()
        try {
            val transform = PhotoTransform().followedBy(PhotoOperation.RotateClockwise)
                .followedBy(PhotoOperation.FlipHorizontal)
            val uri = CropExporter(context).save(Uri.fromFile(source), transform, CropBox(0.25f, 0.25f, 0.75f, 0.75f))
            saved = uri
            val output = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            assertEquals(150, output.width)
            assertEquals(200, output.height)
            assertTrue(Color.blue(output.getPixel(75, 100)) > 200)
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null).use {
                assertTrue(checkNotNull(it).moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            assertArrayEquals(original, source.readBytes())
            output.recycle()
        } finally {
            saved?.let { context.contentResolver.delete(it, null, null) }
            source.delete()
        }
    }
}
