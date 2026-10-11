package com.example.okulo.photo

import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.example.okulo.image.readPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoSavingTest {
    @Test
    fun repeatedSaveRequestsProduceOnePhotoAndReleaseTheSaveControl() = PhotoTestHarness().use { fixture ->
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "save-source.png")
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val gallery = ExportGallery(File(context.cacheDir, "saved.jpg"))
        gallery.attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, gallery)
        fixture.reader = { readPhoto(context.contentResolver, it) }
        fixture.select(Uri.fromFile(source))
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy }
        fixture.model.saveCrop()
        assertTrue(fixture.model.saving.value)
        fixture.model.saveCrop()
        fixture.await { !fixture.model.saving.value }
        assertEquals(1, gallery.insertions)
        val output = BitmapFactory.decodeFile(gallery.file.path)
        assertEquals(16, output.width)
        assertEquals(8, output.height)
    }
}
