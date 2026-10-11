package com.example.okulo.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ActionIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
    tonal: Boolean = false,
    style: ActionIconStyle = if (tonal) ActionIconStyle.Tonal else ActionIconStyle.Plain
) {
    ActionIconButton(painterResource(icon), label, onClick, enabled, iconModifier, style)
}

@Composable
internal fun ActionIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    ActionIconButton(rememberVectorPainter(icon), label, onClick, enabled, Modifier, ActionIconStyle.Tonal, selected)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionIconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    iconModifier: Modifier,
    style: ActionIconStyle = ActionIconStyle.Plain,
    selected: Boolean = false
) {
    val buttonModifier = if (selected) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).semantics { this.selected = true }
    } else {
        Modifier
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        val content: @Composable () -> Unit = {
            val tint by animateColorAsState(LocalContentColor.current, tween(ICON_STATE_MILLIS), label = "icon-color")
            Icon(icon, contentDescription = label, modifier = iconModifier.size(24.dp), tint = tint)
        }
        when (style) {
            ActionIconStyle.Plain -> IconButton(onClick = onClick, enabled = enabled, content = content)
            ActionIconStyle.Filled -> FilledIconButton(onClick = onClick, enabled = enabled, content = content)
            ActionIconStyle.Tonal -> FilledTonalIconButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled,
                content = content
            )
            ActionIconStyle.Outlined -> OutlinedIconButton(onClick = onClick, enabled = enabled, content = content)
        }
    }
}

private const val ICON_STATE_MILLIS = 120

internal enum class ActionIconStyle { Plain, Filled, Tonal, Outlined }
