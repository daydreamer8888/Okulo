package com.example.okulo.photo

import com.example.okulo.composition.CropBox

internal data class CropActions(
    val change: (CropBox) -> Unit = {},
    val finish: () -> Unit = {},
    val restore: () -> Unit = {}
)
