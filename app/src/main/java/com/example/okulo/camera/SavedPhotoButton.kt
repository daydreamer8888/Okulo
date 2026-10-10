package com.example.okulo.camera

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val THUMBNAIL_FADE_MS = 150
private const val THUMBNAIL_SIZE = 128
private const val LEGACY_SAMPLE_SIZE = 16

@Composable
internal fun SavedPhotoButton(uri: Uri?, onClick: () -> Unit) {
    if (uri == null) {
        Spacer(Modifier.size(56.dp))
        return
    }
    val resolver = LocalContext.current.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val photo = produceState(PhotoThumbnail(), uri, resolver, revision) {
        value = PhotoThumbnail()
        value = withContext(Dispatchers.IO) { loadPhotoThumbnail(resolver, uri) }
    }.value
    val bitmap = photo.bitmap
    IconButton(
        onClick = onClick,
        enabled = photo.available,
        modifier = Modifier.size(56.dp).semantics { contentDescription = "查看最新照片" }
    ) {
        val shape = CircleShape
        AnimatedVisibility(
            visible = bitmap != null,
            enter = fadeIn(tween(THUMBNAIL_FADE_MS)),
            exit = fadeOut(tween(THUMBNAIL_FADE_MS))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    null,
                    Modifier.size(48.dp).clip(shape).testTag("saved-photo-thumbnail"),
                    contentScale = ContentScale.Crop
                )
            }
        }
        if (bitmap == null) Box(Modifier.size(48.dp).background(Color.DarkGray, shape))
    }
}

private data class PhotoThumbnail(val available: Boolean = false, val bitmap: Bitmap? = null)

private fun loadPhotoThumbnail(resolver: ContentResolver, uri: Uri?): PhotoThumbnail {
    if (uri == null || !photoAvailable(resolver, uri)) return PhotoThumbnail()
    val bitmap = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), null)
        } else {
            legacyThumbnail(resolver, uri)
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
    return PhotoThumbnail(available = true, bitmap = bitmap)
}

private fun photoAvailable(resolver: ContentResolver, uri: Uri): Boolean = try {
    val trashed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.authority == MediaStore.AUTHORITY &&
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), null, null, null)?.use {
            !it.moveToFirst() || it.getInt(0) != 0
        } != false
    !trashed && resolver.openFileDescriptor(uri, "r")?.use { true } == true
} catch (_: IOException) {
    false
} catch (_: SecurityException) {
    false
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
