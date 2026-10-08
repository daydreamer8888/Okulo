package com.example.okulo.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.ui.ActionIconButton

@Composable
internal fun PhotoToolbar(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    onRestore: (() -> Unit)? = null,
    onAnalyze: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIconButton(R.drawable.ic_arrow_back, "返回拍摄", onBack)
        Spacer(Modifier.weight(1f))
        onAnalyze?.let { ActionIconButton(R.drawable.ic_analyze, "分析构图", it) }
        onCancel?.let { ActionIconButton(R.drawable.ic_close, "取消", it) }
        onRestore?.let { ActionIconButton(R.drawable.ic_restore, "恢复推荐", it) }
        ActionIconButton(R.drawable.ic_save, "保存裁剪", onSave, enabled = canSave)
        ActionIconButton(R.drawable.ic_settings, "设置", onSettings)
    }
}
