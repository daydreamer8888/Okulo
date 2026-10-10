package com.example.okulo.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import kotlin.math.roundToInt

@Composable
internal fun CropWarningSetting(percent: Int, onChange: (Int) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(percent) { mutableStateOf(percent) }
    ListItem(
        colors = settingsItemColors(),
        headlineContent = { Text("裁剪面积提醒") },
        supportingContent = { Text(thresholdDescription(if (editing) draft else percent)) },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.rotate(if (editing) HALF_TURN else 0f))
        },
        modifier = Modifier.clickable {
            draft = percent
            editing = !editing
        }.semantics { stateDescription = if (editing) "已展开" else "已收起" }
    )
    AnimatedVisibility(editing) {
        CropWarningControls(
            draft,
            { draft = it },
            {
                draft = percent
                editing = false
            },
            {
                onChange(draft)
                editing = false
            }
        )
    }
}

@Composable
private fun CropWarningControls(percent: Int, onDraft: (Int) -> Unit, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Slider(
            value = percent.toFloat(),
            onValueChange = { onDraft(it.roundToInt()) },
            valueRange = CROP_WARNING_MIN_PERCENT.toFloat()..CROP_WARNING_MAX_PERCENT.toFloat(),
            steps = (CROP_WARNING_MAX_PERCENT - CROP_WARNING_MIN_PERCENT) / CROP_WARNING_STEP_PERCENT - 1,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "裁剪面积提醒阈值"
                stateDescription = "$percent%"
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = onConfirm) { Text("确定") }
        }
    }
}

private fun thresholdDescription(percent: Int) = "小于原图的 $percent% 时提醒裁剪区域较小"

private const val HALF_TURN = 180f
