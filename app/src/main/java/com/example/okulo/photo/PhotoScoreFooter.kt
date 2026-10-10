@file:Suppress("MagicNumber") // Compact auxiliary score spacing and progress dimensions.

package com.example.okulo.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.ui.CompositionScoreCard
import com.example.okulo.ui.theme.LocalWarningColor

@Composable
internal fun SmallCropWarning(state: PhotoState) {
    val smallCrop = state.displayedCrop?.area?.let { it < 0.5f } == true
    Row(
        modifier = Modifier.auxiliaryVisibility(smallCrop),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(16.dp), tint = LocalWarningColor.current)
        Text("裁剪区域较小", style = MaterialTheme.typography.labelMedium, color = LocalWarningColor.current)
    }
}

@Composable
internal fun PhotoScoreCard(state: PhotoState, showScores: Boolean) {
    CompositionScoreCard(state.originalScore, state.displayedScore, showScores, Modifier.testTag("photo-scores"))
}

private fun Modifier.auxiliaryVisibility(visible: Boolean): Modifier =
    if (visible) this else alpha(0f).clearAndSetSemantics {}
