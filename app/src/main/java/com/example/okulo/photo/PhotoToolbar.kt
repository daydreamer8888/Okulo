package com.example.okulo.photo

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.example.okulo.R
import com.example.okulo.ui.ActionIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoToolbar(onBack: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = { Text("裁剪") },
        navigationIcon = { ActionIconButton(R.drawable.ic_arrow_back, "返回拍摄", onBack) },
        actions = { ActionIconButton(R.drawable.ic_settings, "设置", onSettings) },
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}
