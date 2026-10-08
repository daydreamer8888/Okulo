package com.example.okulo.camera

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val CAPTURE_MESSAGE_DURATION_MS = 2_000L

@Composable
internal fun CaptureFeedback(capture: CaptureUiState, onDismiss: () -> Unit) {
    var message by remember(capture.message, capture.savedPhoto, capture.saving) {
        mutableStateOf(if (capture.saving) null else capture.message)
    }
    val dismiss by rememberUpdatedState(onDismiss)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(capture.message, capture.savedPhoto, capture.saving) {
        if (message != null) {
            val duration = accessibility?.calculateRecommendedTimeoutMillis(
                CAPTURE_MESSAGE_DURATION_MS, containsText = true
            ) ?: CAPTURE_MESSAGE_DURATION_MS
            delay(duration)
            message = null
            dismiss()
        }
    }
    Text(
        if (capture.saving) "正在保存…" else message.orEmpty(),
        Modifier.padding(top = 12.dp),
        color = Color.White,
        style = MaterialTheme.typography.bodySmall
    )
}
