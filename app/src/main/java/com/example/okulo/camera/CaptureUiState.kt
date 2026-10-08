package com.example.okulo.camera

import android.net.Uri

internal data class CaptureUiState(
    val ready: Boolean = false,
    val saving: Boolean = false,
    val savedPhoto: Uri? = null,
    val message: String? = null
)
