package com.example.okulo.camera

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.okulo.image.GalleryAlbum

/** Query the camera album through MediaStore, which limits results to accessible photos. */
@Suppress("DEPRECATION") // Older Android versions identify albums by their file paths.
internal fun latestCameraPhoto(resolver: ContentResolver): Uri? {
    val folder = GalleryAlbum.Camera.relativePath
    val selection = mutableListOf<String>()
    val args: Array<String>
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        selection += "${MediaStore.Images.Media.RELATIVE_PATH} IN (?, ?)"
        args = arrayOf(folder, "$folder/")
        selection += "${MediaStore.Images.Media.IS_PENDING} = 0"
    } else {
        selection += "${MediaStore.Images.Media.DATA} LIKE ?"
        args = arrayOf("%/$folder/%")
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) selection += "${MediaStore.Images.Media.IS_TRASHED} = 0"
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    return try {
        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            selection.joinToString(" AND "),
            args,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC, " +
                "${MediaStore.Images.Media._ID} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
    } catch (_: SecurityException) {
        null
    }
}
