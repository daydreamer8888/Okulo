package com.example.okulo.image

import android.os.Environment

internal enum class GalleryAlbum(val relativePath: String) {
    Camera("${Environment.DIRECTORY_DCIM}/Camera"),
    Edits("${Environment.DIRECTORY_PICTURES}/Okulo")
}
