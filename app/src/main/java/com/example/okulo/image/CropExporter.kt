package com.example.okulo.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.okulo.composition.CropBox
import java.io.IOException

/** Export original pixels using the same orientation and normalized crop as the editor. */
internal class CropExporter(context: Context) : CropWriter {
    private val resolver = context.applicationContext.contentResolver

    @Suppress("TooGenericExceptionCaught") // Roll back a gallery row for storage and encoding failures.
    override fun save(source: Uri, transform: PhotoTransform, crop: CropBox): Uri {
        val original = readFullPhoto(resolver, source)
        var edited: Bitmap? = null
        var output: Bitmap? = null
        var destination: Uri? = null
        try {
            edited = transform.apply(original)
            output = cropPreview(edited, crop)
            val uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                galleryPhotoValues("Okulo_crop", pending = true)
            )
                ?: throw IOException("无法创建相册照片")
            destination = uri
            writePhoto(checkNotNull(output), uri)
            publishPhoto(uri)
            return uri
        } catch (failure: Exception) {
            destination?.let { runCatching { resolver.delete(it, null, null) } }
            throw failure
        } finally {
            listOfNotNull(original, edited, output).distinct().forEach { it.recycle() }
        }
    }

    private fun writePhoto(bitmap: Bitmap, uri: Uri) {
        resolver.openOutputStream(uri)?.use {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) throw IOException("照片编码失败")
        } ?: throw IOException("无法写入相册照片")
    }

    private fun publishPhoto(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            if (resolver.update(uri, ready, null, null) != 1) throw IOException("无法完成照片保存")
        }
    }
}

private const val JPEG_QUALITY = 95
