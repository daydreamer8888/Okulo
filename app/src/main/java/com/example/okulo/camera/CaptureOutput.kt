package com.example.okulo.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID

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
            photoValues()
        ).build()
    )
}

@Suppress("DEPRECATION")
private fun photoValues(): ContentValues {
    val name = "Okulo_${UUID.randomUUID()}.jpg"
    return ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Okulo")
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Okulo")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create photo directory")
            put(MediaStore.Images.Media.DATA, File(directory, name).absolutePath)
        }
    }
}
