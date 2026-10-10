package com.example.okulo.ui

import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun FeedbackSnackbar(message: String, modifier: Modifier = Modifier) {
    Snackbar(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(message)
    }
}

@Composable
internal fun rememberFeedbackMessage(events: Flow<String>?): String? {
    var message by remember(events) { mutableStateOf<String?>(null) }
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(events, accessibility) {
        events?.collectLatest {
            message = it
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                FEEDBACK_DURATION_MILLIS, containsText = true
            ) ?: FEEDBACK_DURATION_MILLIS
            try {
                delay(timeout)
            } finally {
                message = null
            }
        }
    }
    return message
}

private const val FEEDBACK_DURATION_MILLIS = 2_000L
