package com.example.okulo.camera

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val THUMBNAIL_SIZE = 128
private const val LEGACY_SAMPLE_SIZE = 16

@Composable
internal fun SavedPhotoButton(uri: Uri?, onClick: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val bitmap = produceState<Bitmap?>(null, uri, resolver) {
        value = withContext(Dispatchers.IO) {
            try {
                when {
                    uri == null -> null
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                        resolver.loadThumbnail(uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), null)
                    else -> legacyThumbnail(resolver, uri)
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
    }.value
    IconButton(
        onClick = onClick,
        enabled = uri != null,
        modifier = Modifier.size(56.dp).semantics { contentDescription = "查看最新照片" }
    ) {
        val shape = RoundedCornerShape(12.dp)
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), null, Modifier.size(48.dp).clip(shape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(48.dp).background(Color.DarkGray, shape))
        }
    }
}

private fun legacyThumbnail(resolver: ContentResolver, uri: Uri): Bitmap? {
    val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    val bitmap = resolver.openInputStream(uri)?.use {
        val options = BitmapFactory.Options().apply { inSampleSize = LEGACY_SAMPLE_SIZE }
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    return if (rotation == 0) {
        bitmap
    } else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { bitmap.recycle() }
    }
}
