@file:Suppress("MagicNumber") // Compact auxiliary score spacing and progress dimensions.

package com.example.okulo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun CompositionScoreCard(
    originalScore: Float?,
    cropScore: Float?,
    showScores: Boolean,
    modifier: Modifier = Modifier.testTag("composition-scores"),
    loading: Boolean = cropScore == null
) {
    val visible = showScores && originalScore != null
    Surface(
        modifier = if (visible) modifier else Modifier.alpha(0f).clearAndSetSemantics {},
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreValue("原图", originalScore ?: 0f)
            ScoreValue("裁剪", if (visible) cropScore else 0f, loading)
        }
    }
}

@Composable
private fun ScoreValue(label: String, score: Float?, loading: Boolean = false) {
    val numberStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Box(contentAlignment = Alignment.Center) {
            Text(
                "-00.00",
                Modifier.alpha(0f).clearAndSetSemantics {},
                style = numberStyle
            )
            if (score == null && loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp).semantics { contentDescription = "正在更新裁剪评分" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    strokeWidth = 1.5.dp
                )
            } else {
                Text(
                    score?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "—",
                    style = numberStyle
                )
            }
        }
    }
}
