package com.example.okulo.photo

import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.example.okulo.composition.CropBox
import com.example.okulo.image.CropWriter
import com.example.okulo.image.PhotoOperation
import com.example.okulo.image.PhotoTransform
import com.example.okulo.image.readPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoSavingTest {
    @Test
    fun savingKeepsTheRequestedEditWhenPhotosChangeAndAllowsRetryAfterFailure() {
        val first = Uri.parse("content://photos/first")
        val second = Uri.parse("content://photos/second")
        val crop = CropBox(0.2f, 0.3f, 0.8f, 0.7f)
        val writes = mutableListOf<Triple<Uri, PhotoTransform, CropBox>>()
        lateinit var gate: WorkGate
        val writer = CropWriter { source, transform, box ->
            writes.add(Triple(source, transform, box))
            if (writes.size == 1) {
                gate.block()
                throw IOException("Storage unavailable")
            }
            Uri.parse("content://saved/crop")
        }
        PhotoTestHarness(writer).use { fixture ->
            gate = fixture.gate()
            val messages = mutableListOf<String>()
            val collector = CoroutineScope(Dispatchers.Main.immediate).launch {
                fixture.model.saveEvents.collect { messages.add(it) }
            }
            try {
                fixture.select(first)
                fixture.model.analyze()
                fixture.await { !fixture.model.state.value.busy }
                fixture.model.transformPhoto(PhotoOperation.RotateClockwise)
                fixture.await { !fixture.model.state.value.busy }
                fixture.model.updateCrop(crop)
                fixture.model.saveCrop()
                gate.awaitEntry()
                fixture.select(second)
                fixture.model.saveCrop()
                assertTrue(fixture.model.saving.value)
                gate.release()
                fixture.await { !fixture.model.saving.value && messages.size == 1 }
                assertEquals(listOf(Triple(first, PhotoTransform(turns = 1), crop)), writes)
                assertEquals(listOf("保存失败，请重试"), messages)
                fixture.model.analyze()
                fixture.await { !fixture.model.state.value.busy }
                fixture.model.saveCrop()
                fixture.await { !fixture.model.saving.value && messages.size == 2 }
                assertEquals(second, writes.last().first)
                assertEquals(PhotoTransform(), writes.last().second)
                assertEquals(listOf("保存失败，请重试", "已保存到相册"), messages)
            } finally {
                collector.cancel()
            }
        }
    }

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
