package com.example.okulo.image

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.IOException

/** Publish the captured JPEG without resampling or stripping its metadata. */
@Suppress("TooGenericExceptionCaught") // Remove incomplete gallery rows if copying or publication fails.
internal fun saveOriginalPhoto(resolver: ContentResolver, source: Uri): Uri {
    val destination = resolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        galleryPhotoValues("Okulo", pending = true)
    ) ?: throw IOException("无法创建相册照片")
    try {
        copyOriginalPhoto(resolver, source, destination)
        publishOriginalPhoto(resolver, destination)
        return destination
    } catch (failure: Exception) {
        runCatching { resolver.delete(destination, null, null) }
        throw failure
    }
}

private fun copyOriginalPhoto(resolver: ContentResolver, source: Uri, destination: Uri) {
    val bytes = resolver.openInputStream(source)?.use { input ->
        resolver.openOutputStream(destination)?.use { output -> input.copyTo(output) }
    } ?: throw IOException("无法保存原图")
    if (bytes == 0L) throw IOException("原图为空")
}

private fun publishOriginalPhoto(resolver: ContentResolver, destination: Uri) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        if (resolver.update(destination, ready, null, null) != 1) throw IOException("无法完成照片保存")
    }
}
