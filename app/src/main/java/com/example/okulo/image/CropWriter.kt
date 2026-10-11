package com.example.okulo.image

import android.net.Uri
import com.example.okulo.composition.CropBox

/** Writes a transformed crop from source pixels and returns its gallery location. */
internal fun interface CropWriter {
    fun save(source: Uri, transform: PhotoTransform, crop: CropBox): Uri
}
