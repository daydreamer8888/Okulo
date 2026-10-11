package com.example.okulo.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.okulo.image.readPhoto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoReaderTest {
    @get:Rule val files = TemporaryFolder()

    @Test
    @Config(sdk = [26, 28])
    fun importPreservesAllEightExifOrientationsAndProducesSoftwarePixels() {
        val original = writePhoto(22, 33)
        val baseline = BitmapFactory.decodeFile(original.absolutePath)
        val colors = intArrayOf(
            baseline.getPixel(5, 5),
            baseline.getPixel(16, 5),
            baseline.getPixel(5, 16),
            baseline.getPixel(16, 16),
            baseline.getPixel(5, 27),
            baseline.getPixel(16, 27)
        )
        baseline.recycle()
        val cases = listOf(
            intArrayOf(0, 1, 2, 3, 4, 5),
            intArrayOf(1, 0, 3, 2, 5, 4),
            intArrayOf(5, 4, 3, 2, 1, 0),
            intArrayOf(4, 5, 2, 3, 0, 1),
            intArrayOf(0, 2, 4, 1, 3, 5),
            intArrayOf(4, 2, 0, 5, 3, 1),
            intArrayOf(5, 3, 1, 4, 2, 0),
            intArrayOf(1, 3, 5, 0, 2, 4)
        )
        for ((index, order) in cases.withIndex()) {
            ExifInterface(original).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, (index + 1).toString())
                saveAttributes()
            }
            val imported = read(original)
            try {
                assertTrue(imported.config != Bitmap.Config.HARDWARE)
                val columns = if (index < 4) 2 else 3
                assertEquals(columns * 11, imported.width)
                assertEquals(if (index < 4) 33 else 22, imported.height)
                val actual = IntArray(6) { cell -> imported.getPixel(cell % columns * 11 + 5, cell / columns * 11 + 5) }
                assertArrayEquals("EXIF ${index + 1}", order.map { colors[it] }.toIntArray(), actual)
            } finally {
                imported.recycle()
            }
        }
    }

    @Test
    @Config(sdk = [29])
    fun modernImportConvertsWideGamutPhotosToSrgbForModelInput() {
        val bitmap = Bitmap.createBitmap(
            8,
            6,
            Bitmap.Config.ARGB_8888,
            true,
            ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        )
        val source = files.newFile("wide-gamut.png")
        try {
            bitmap.eraseColor(0xffff0000.toInt())
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        val encoded = BitmapFactory.decodeFile(source.absolutePath)
        try {
            assertTrue("Fixture lost its wide-gamut profile", checkNotNull(encoded.colorSpace).isWideGamut)
        } finally {
            encoded.recycle()
        }
        val imported = read(source)
        try {
            assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), imported.colorSpace)
            assertEquals(8, imported.width)
            assertEquals(6, imported.height)
        } finally {
            imported.recycle()
        }
    }

    @Test
    fun modernImportLimitsLongestSideWhileKeepingAspectRatio() {
        val imported = read(writePhoto(3000, 1000))
        try {
            assertEquals(2048, imported.width)
            assertEquals(683, imported.height)
        } finally {
            imported.recycle()
        }
    }

    @Test
    @Config(sdk = [26])
    fun legacyImportDownsamplesLargePhotos() {
        val imported = read(writePhoto(3000, 1000))
        try {
            assertEquals(1500, imported.width)
            assertEquals(500, imported.height)
        } finally {
            imported.recycle()
        }
    }

    @Test(expected = IllegalStateException::class)
    @Config(sdk = [26])
    fun legacyImportRejectsInvalidImageData() {
        val invalid = files.newFile("invalid.jpg").apply { writeText("invalid image") }
        read(invalid)
    }

    @Test(expected = FileNotFoundException::class)
    fun missingPhotoFailsInsteadOfReturningAPlaceholder() {
        read(File(files.root, "missing.jpg"))
    }

    private fun read(file: File): Bitmap =
        readPhoto(RuntimeEnvironment.getApplication().contentResolver, Uri.fromFile(file))

    private fun writePhoto(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(
            0xffff0000.toInt(),
            0xff00ff00.toInt(),
            0xff0000ff.toInt(),
            0xffffff00.toInt(),
            0xff00ffff.toInt(),
            0xffff00ff.toInt()
        )
        val pixels = IntArray(width * height) { index ->
            colors[((index / width) * 3 / height) * 2 + (index % width) * 2 / width]
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return try {
            val file = files.newFile()
            file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output) }
            file
        } finally {
            bitmap.recycle()
        }
    }
}
