package com.example.okulo.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.ui.ActionIconButton
import com.example.okulo.ui.ActionIconStyle
import com.example.okulo.ui.CompositionScoreCard

internal data class CameraCompositionActions(
    val recommend: () -> Unit = {},
    val restore: () -> Unit = {},
    val dismiss: () -> Unit = {},
    val dismissError: () -> Unit = {}
)

@Composable
internal fun CameraCompositionTools(
    state: CameraCompositionState,
    actions: CameraCompositionActions,
    showScores: Boolean
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            CompositionScoreCard(
                state.originalScore,
                if (state.busy) state.cropScore ?: state.recommendation?.cropScore else state.cropScore,
                showScores && state.crop != null,
                loading = !state.busy && state.scoring
            )
        }
        Row(if (state.active) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}) {
            ActionIconButton(
                R.drawable.ic_restore,
                "恢复推荐",
                actions.restore,
                enabled = state.canRestore && !state.busy,
                style = ActionIconStyle.Outlined
            )
            ActionIconButton(
                R.drawable.ic_close,
                if (state.busy) "取消推荐" else "关闭推荐",
                actions.dismiss,
                enabled = state.active,
                style = ActionIconStyle.Outlined
            )
        }
    }
}

@Composable
internal fun CameraRecommendButton(state: CameraCompositionState, ready: Boolean, onRecommend: () -> Unit) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        ActionIconButton(
            R.drawable.ic_analyze,
            "推荐构图",
            onRecommend,
            enabled = ready && !state.busy,
            iconModifier = if (state.busy) Modifier.alpha(0f) else Modifier,
            style = ActionIconStyle.Tonal
        )
        if (state.busy) {
            CircularProgressIndicator(
                Modifier.size(24.dp).semantics { contentDescription = "正在推荐构图" },
                strokeWidth = 2.dp
            )
        }
    }
}
