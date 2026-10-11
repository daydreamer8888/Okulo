package com.example.okulo.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.example.okulo.R
import com.example.okulo.ui.ActionIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    showScores: Boolean,
    onScores: (Boolean) -> Unit,
    onBack: () -> Unit,
    appearance: @Composable () -> Unit = {},
    keepOriginal: Boolean = true,
    onKeepOriginal: (Boolean) -> Unit = {},
    cropWarningPercent: Int = DEFAULT_CROP_WARNING_PERCENT,
    onCropWarning: (Int) -> Unit = {},
    freezeComposition: Boolean = false,
    onFreezeComposition: (Boolean) -> Unit = {}
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = { ActionIconButton(R.drawable.ic_arrow_back, "返回", onBack) },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            appearance()
            SettingsGroup("拍摄") {
                ListItem(
                    colors = settingsItemColors(),
                    headlineContent = { Text("推荐构图时定格画面") },
                    trailingContent = { Switch(checked = freezeComposition, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        freezeComposition,
                        role = Role.Switch,
                        onValueChange = onFreezeComposition
                    )
                )
                ListItem(
                    colors = settingsItemColors(),
                    headlineContent = { Text("同时保存原图") },
                    trailingContent = { Switch(checked = keepOriginal, onCheckedChange = null) },
                    modifier = Modifier.toggleable(keepOriginal, role = Role.Switch, onValueChange = onKeepOriginal)
                )
            }
            AnalysisSettings(showScores, onScores) {
                CropWarningSetting(cropWarningPercent, onCropWarning)
            }
        }
    }
}

@Composable
private fun AnalysisSettings(
    showScores: Boolean,
    onScores: (Boolean) -> Unit,
    cropWarning: @Composable () -> Unit
) {
    SettingsGroup("分析") {
        ListItem(
            colors = settingsItemColors(),
            headlineContent = { Text("显示评分") },
            supportingContent = { Text("仅用于比较同一张照片的不同裁剪") },
            trailingContent = { Switch(checked = showScores, onCheckedChange = null) },
            modifier = Modifier.toggleable(showScores, role = Role.Switch, onValueChange = onScores)
        )
        cropWarning()
    }
}
