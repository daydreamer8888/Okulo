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
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun SmallCropWarning(state: PhotoState) {
    val smallCrop = hasSmallOutput(state)
    Row(
        modifier = Modifier.auxiliaryVisibility(smallCrop),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(16.dp), tint = LocalWarningColor.current)
        Text("裁剪后尺寸较小", style = MaterialTheme.typography.labelMedium, color = LocalWarningColor.current)
    }
}

@Composable
internal fun PhotoScoreCard(state: PhotoState, showScores: Boolean) {
    CompositionScoreCard(state.originalScore, state.displayedScore, showScores, Modifier.testTag("photo-scores"))
}

private fun Modifier.auxiliaryVisibility(visible: Boolean): Modifier =
    if (visible) this else alpha(0f).clearAndSetSemantics {}

private fun hasSmallOutput(state: PhotoState): Boolean {
    val crop = state.displayedCrop
    val photo = state.photo
    if (crop == null || photo == null) return false
    val source = state.sourceSize
    val rotated = state.transform.turns % 2 != 0
    val width = if (source == null) photo.width else if (rotated) source.height else source.width
    val height = if (source == null) photo.height else if (rotated) source.width else source.height
    val outputWidth = ceil(crop.right * width).toLong() - floor(crop.left * width).toLong()
    val outputHeight = ceil(crop.bottom * height).toLong() - floor(crop.top * height).toLong()
    return outputWidth * outputHeight < SMALL_OUTPUT_PIXELS
}

private const val SMALL_OUTPUT_PIXELS = 1_000_000L
