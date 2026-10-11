package com.example.okulo.ui.crop

import com.example.okulo.composition.CropBox

internal data class CropActions(
    val change: (CropBox) -> Unit = {},
    val finish: () -> Unit = {},
    val restore: () -> Unit = {}
)
