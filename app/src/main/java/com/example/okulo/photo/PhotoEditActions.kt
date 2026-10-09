package com.example.okulo.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.composition.CropAspect
import com.example.okulo.ui.ActionIconButton

internal data class PhotoEditActions(
    val analyze: () -> Unit,
    val cancel: () -> Unit,
    val save: () -> Unit,
    val restore: () -> Unit,
    val transform: (PhotoOperation) -> Unit
)

@Composable
internal fun PhotoActionRow(state: PhotoState, actions: PhotoEditActions, saving: Boolean) {
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) {
            ActionIconButton(R.drawable.ic_close, "取消", actions.cancel, tonal = true)
        } else {
            val canAnalyze = state.photo != null &&
                (state.result == null || (state.aspect == CropAspect.Free && state.manualCrop != null))
            ActionIconButton(
                R.drawable.ic_analyze,
                "分析构图",
                actions.analyze,
                enabled = canAnalyze && !saving,
                tonal = true
            )
        }
        ActionIconButton(
            R.drawable.ic_save,
            "保存",
            actions.save,
            enabled = state.displayedCrop != null && !state.busy && !saving,
            tonal = true
        )
        ActionIconButton(
            R.drawable.ic_restore,
            "恢复",
            actions.restore,
            enabled = state.result != null && state.manualCrop != null && !state.busy && !saving,
            tonal = true
        )
        Spacer(Modifier.weight(1f))
        PhotoTransformMenu(state.photo != null && !state.busy && !saving, actions.transform)
    }
}

@Composable
private fun PhotoTransformMenu(enabled: Boolean, onTransform: (PhotoOperation) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIconButton(R.drawable.ic_more, "更多", { expanded = true }, enabled = enabled)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val operations = listOf(
                "向右旋转" to PhotoOperation.RotateClockwise,
                "水平翻转" to PhotoOperation.FlipHorizontal,
                "垂直翻转" to PhotoOperation.FlipVertical
            )
            operations.forEach { (label, operation) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    enabled = enabled,
                    onClick = {
                        expanded = false
                        onTransform(operation)
                    }
                )
            }
        }
    }
}
