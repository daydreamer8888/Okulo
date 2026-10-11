@file:Suppress("MagicNumber") // Focus geometry and vertical exposure control dimensions.

package com.example.okulo.camera

import androidx.camera.view.CameraController
import androidx.camera.view.TapToFocusInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun CameraMeteringControls(state: CameraMeteringState, onExposure: (Float) -> Unit) {
    val focus = state.focus?.takeIf { it.tapPoint != null } ?: return
    val point = checkNotNull(focus.tapPoint)
    var visible by remember(focus) { mutableStateOf(true) }
    val interactions = remember { MutableInteractionSource() }
    val dragging by interactions.collectIsDraggedAsState()
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(focus, state.revision, dragging) {
        visible = true
        if (!dragging) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(3000, containsControls = true) ?: 3000)
            visible = false
        }
    }
    if (!visible) return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val half = with(density) { 32.dp.toPx() }
        val x = point.x.coerceIn(half, (width - half).coerceAtLeast(half))
        val y = point.y.coerceIn(half, (height - half).coerceAtLeast(half))
        CameraFocusIndicator(focus, Modifier.offset { IntOffset((x - half).roundToInt(), (y - half).roundToInt()) })
        state.range?.let { range ->
            val panelWidth = with(density) { 48.dp.toPx() }
            val panelHeight = with(density) { 160.dp.toPx() }
            val gap = with(density) { 8.dp.toPx() }
            val panelX = if (x + half + gap + panelWidth <= width) x + half + gap else x - half - gap - panelWidth
            val panelY = (y - panelHeight / 2).coerceIn(0f, (height - panelHeight).coerceAtLeast(0f))
            ExposurePanel(
                state.index,
                range,
                onExposure,
                interactions,
                Modifier.offset { IntOffset(panelX.coerceAtLeast(0f).roundToInt(), panelY.roundToInt()) }
            )
        }
    }
}

@Composable
private fun CameraFocusIndicator(info: TapToFocusInfo, modifier: Modifier) {
    val failed = info.focusState == CameraController.TAP_TO_FOCUS_FAILED ||
        info.focusState == CameraController.TAP_TO_FOCUS_NOT_FOCUSED
    val focused = info.focusState == CameraController.TAP_TO_FOCUS_FOCUSED
    val color by animateColorAsState(
        if (failed) MaterialTheme.colorScheme.error else Color.White,
        tween(120),
        label = "focus-color"
    )
    val label = when {
        failed -> "对焦失败"
        focused -> "已对焦"
        else -> "正在对焦"
    }
    Canvas(modifier.size(64.dp).semantics { contentDescription = label }) {
        val inset = 4.dp.toPx()
        val arm = 12.dp.toPx()
        val corners = Path().apply {
            listOf(
                Offset(inset, inset),
                Offset(size.width - inset, inset),
                Offset(inset, size.height - inset),
                Offset(size.width - inset, size.height - inset)
            ).forEach {
                val dx = if (it.x < center.x) arm else -arm
                val dy = if (it.y < center.y) arm else -arm
                moveTo(it.x + dx, it.y)
                lineTo(it.x, it.y)
                lineTo(it.x, it.y + dy)
            }
        }
        drawPath(corners, Color.Black.copy(alpha = 0.7f), style = Stroke(4.dp.toPx()))
        drawPath(corners, color, style = Stroke(2.dp.toPx()))
        if (failed) {
            drawLine(
                color,
                center - Offset(4.dp.toPx(), 4.dp.toPx()),
                center + Offset(4.dp.toPx(), 4.dp.toPx()),
                2.dp.toPx()
            )
            drawLine(
                color,
                center + Offset(-4.dp.toPx(), 4.dp.toPx()),
                center + Offset(4.dp.toPx(), -4.dp.toPx()),
                2.dp.toPx()
            )
        } else {
            drawCircle(color, 2.dp.toPx())
        }
    }
}

@Composable
private fun ExposurePanel(
    index: Int,
    range: IntRange,
    onExposure: (Float) -> Unit,
    interactions: MutableInteractionSource,
    modifier: Modifier
) {
    Surface(modifier.size(48.dp, 160.dp).testTag("exposure-panel"), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExposureIcon()
            Box(Modifier.size(48.dp, 124.dp), contentAlignment = Alignment.Center) {
                Slider(
                    value = index.toFloat(),
                    onValueChange = onExposure,
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first - 1).coerceAtLeast(0),
                    interactionSource = interactions,
                    modifier = Modifier.requiredSize(120.dp, 48.dp).rotate(-90f)
                        .semantics { contentDescription = "曝光补偿" }
                )
            }
        }
    }
}

@Composable
private fun ExposureIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(20.dp)) {
        drawCircle(color, 3.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        repeat(8) { ray ->
            val angle = ray * PI / 4
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(color, center + direction * 5.dp.toPx(), center + direction * 8.dp.toPx(), 1.5.dp.toPx())
        }
    }
}
