package com.example.okulo.image

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.util.UUID

/** JPEG metadata and publication fields for the selected gallery album. */
@Suppress("DEPRECATION")
internal fun galleryPhotoValues(
    prefix: String,
    pending: Boolean = false,
    album: GalleryAlbum = GalleryAlbum.Edits
): ContentValues {
    val name = "${prefix}_${UUID.randomUUID()}.jpg"
    return ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, album.relativePath)
            if (pending) put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val directory = File(Environment.getExternalStorageDirectory(), album.relativePath)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建照片目录")
            put(MediaStore.Images.Media.DATA, File(directory, name).absolutePath)
        }
    }
}
