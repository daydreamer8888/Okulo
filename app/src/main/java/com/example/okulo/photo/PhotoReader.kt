package com.example.okulo.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import kotlin.math.roundToInt

private const val MAX_PHOTO_SIDE = 2048

fun readPhoto(resolver: ContentResolver, uri: Uri): Bitmap = readPhotoScaled(resolver, uri, MAX_PHOTO_SIDE)

internal fun readFullPhoto(resolver: ContentResolver, uri: Uri): Bitmap = readPhotoScaled(resolver, uri, Int.MAX_VALUE)

internal fun readPhotoSize(resolver: ContentResolver, uri: Uri): Size {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
    check(options.outWidth > 0 && options.outHeight > 0) { "无法读取这张照片" }
    val swapped = resolver.openInputStream(uri).use { stream ->
        checkNotNull(stream) { "照片已不可访问，请重新选择" }
        ExifInterface(stream).rotationDegrees % HALF_TURN != 0f
    }
    return if (swapped) Size(options.outHeight, options.outWidth) else Size(options.outWidth, options.outHeight)
}

private fun readPhotoScaled(resolver: ContentResolver, uri: Uri, maximumSide: Int): Bitmap =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val scale = minOf(1.0, maximumSide.toDouble() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1)
            )
        }
    } else {
        readLegacyPhoto(resolver, uri, maximumSide)
    }

private fun readLegacyPhoto(resolver: ContentResolver, uri: Uri, maximumSide: Int): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
    check(options.outWidth > 0 && options.outHeight > 0) { "无法读取这张照片" }
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > maximumSide) {
        options.inSampleSize *= 2
    }
    options.inJustDecodeBounds = false
    val bitmap = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("无法读取这张照片")
    val orientation = resolver.openInputStream(uri).use { stream ->
        checkNotNull(stream) { "照片已不可访问，请重新选择" }
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
    val matrix = orientationMatrix(orientation)
    return if (matrix.isIdentity) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { bitmap.recycle() }
    }
}

internal fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(HALF_TURN)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            setRotate(QUARTER_TURN)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(QUARTER_TURN)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            setRotate(-QUARTER_TURN)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-QUARTER_TURN)
    }
}

private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
