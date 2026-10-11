package com.example.okulo.camera

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import com.example.okulo.image.galleryPhotoValues
import java.io.Closeable
import java.io.File

internal class CaptureOutput(
    val options: ImageCapture.OutputFileOptions,
    private val temporary: File? = null
) : Closeable {
    val keepsOriginal: Boolean get() = temporary == null

    fun source(result: ImageCapture.OutputFileResults): Uri? = temporary?.let(Uri::fromFile) ?: result.savedUri

    override fun close() { temporary?.delete() }
}

internal fun captureOutput(context: Context, cropOnly: Boolean): CaptureOutput {
    if (cropOnly) {
        val file = File.createTempFile("Okulo_capture_", ".jpg", context.cacheDir)
        return CaptureOutput(ImageCapture.OutputFileOptions.Builder(file).build(), file)
    }
    return CaptureOutput(
        ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            galleryPhotoValues("Okulo")
        ).build()
    )
}
