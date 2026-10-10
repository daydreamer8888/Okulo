package com.example.okulo.photo

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun PhotoImageArea(
    state: PhotoState,
    ratio: Float,
    showScores: Boolean,
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit
) {
    Layout(
        content = {
            Box { image() }
            SmallCropWarning(state)
            PhotoScoreCard(state, showScores)
        },
        modifier = modifier
    ) { children, constraints ->
        val padding = 16.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val availableWidth = (width - padding * 2).coerceAtLeast(0)
        val auxiliary = Constraints(maxWidth = availableWidth)
        val warning = children[1].measure(auxiliary)
        val scores = children[2].measure(auxiliary)
        val footerHeight = maxOf(24.dp.roundToPx(), warning.height, scores.height)
        val initialHeight = (height - padding * 2 - footerHeight).coerceAtLeast(0)
        val initialWidth = minOf(availableWidth, (initialHeight * ratio).roundToInt())
        val stacked = warning.width + scores.width + 8.dp.roundToPx() > initialWidth
        val reserved = if (stacked) scores.height + warning.height + 8.dp.roundToPx() else footerHeight
        val availableHeight = (height - padding * 2 - reserved).coerceAtLeast(0)
        val imageWidth = minOf(availableWidth, (availableHeight * ratio).roundToInt())
        val imageHeight = (imageWidth / ratio).roundToInt().coerceAtMost(availableHeight)
        val photo = children[0].measure(Constraints.fixed(imageWidth, imageHeight))
        val x = (width - imageWidth) / 2
        val y = padding + (availableHeight - imageHeight) / 2
        val infoY = y + imageHeight + gap
        layout(width, height) {
            photo.placeRelative(x, y)
            scores.placeRelative((x + imageWidth - scores.width).coerceAtLeast(padding), infoY)
            warning.placeRelative(
                padding,
                if (stacked) infoY + scores.height + 8.dp.roundToPx() else infoY
            )
        }
    }
}
