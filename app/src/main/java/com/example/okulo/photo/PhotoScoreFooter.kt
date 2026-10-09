@file:Suppress("MagicNumber") // Compact auxiliary score spacing and progress dimensions.

package com.example.okulo.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
internal fun PhotoScoreFooter(state: PhotoState, showScores: Boolean) {
    val smallCrop = state.displayedCrop?.area?.let { it < 0.5f } == true
    val visible = showScores && state.originalScore != null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "裁剪范围较小",
            modifier = Modifier.weight(1f).auxiliaryVisibility(smallCrop),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier.auxiliaryVisibility(visible).then(
                if (visible) Modifier.testTag("photo-scores") else Modifier
            ),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreValue("原图", state.originalScore ?: 0f)
                ScoreValue("裁剪", if (visible) state.displayedScore else 0f)
            }
        }
    }
}

@Composable
private fun ScoreValue(label: String, score: Float?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Box(contentAlignment = Alignment.Center) {
            // Retain the same slot for a number or progress, including scaled fonts.
            Text("0.00", Modifier.auxiliaryVisibility(false), style = MaterialTheme.typography.bodySmall)
            if (score == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp).semantics { contentDescription = "正在更新裁剪评分" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    strokeWidth = 1.5.dp
                )
            } else {
                Text(String.format(Locale.getDefault(), "%.2f", score), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun Modifier.auxiliaryVisibility(visible: Boolean): Modifier =
    if (visible) this else alpha(0f).clearAndSetSemantics {}
