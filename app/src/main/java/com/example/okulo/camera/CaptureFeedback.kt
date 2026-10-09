package com.example.okulo.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalAccessibilityManager
import com.example.okulo.ui.FeedbackPill
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
    val text = if (capture.saving) "正在保存…" else message.orEmpty()
    if (text.isNotEmpty()) FeedbackPill(text)
}
