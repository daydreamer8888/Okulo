package com.example.okulo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun FeedbackPill(message: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text(
            message,
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
