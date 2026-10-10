package com.example.okulo.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.ui.ActionIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    showScores: Boolean,
    onScores: (Boolean) -> Unit,
    onBack: () -> Unit,
    analysisMode: AnalysisMode,
    onMode: (AnalysisMode) -> Unit,
    appearance: @Composable () -> Unit = {},
    keepOriginal: Boolean = true,
    onKeepOriginal: (Boolean) -> Unit = {}
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
                    headlineContent = { Text("保留原图") },
                    supportingContent = { Text("裁剪拍摄时同时保存原图") },
                    trailingContent = { Switch(checked = keepOriginal, onCheckedChange = null) },
                    modifier = Modifier.toggleable(keepOriginal, role = Role.Switch, onValueChange = onKeepOriginal)
                )
            }
            AnalysisSettings(showScores, onScores, analysisMode, onMode)
        }
    }
}

@Composable
private fun AnalysisSettings(
    showScores: Boolean,
    onScores: (Boolean) -> Unit,
    analysisMode: AnalysisMode,
    onMode: (AnalysisMode) -> Unit
) {
    var choosingMode by remember { mutableStateOf(false) }
    SettingsGroup("分析") {
        ListItem(
            colors = settingsItemColors(),
            headlineContent = { Text("分析模式") },
            supportingContent = { Text(analysisMode.title) },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
            modifier = Modifier.clickable { choosingMode = true }
        )
        ListItem(
            colors = settingsItemColors(),
            headlineContent = { Text("显示评分") },
            supportingContent = { Text("仅用于比较同一张照片的不同裁剪") },
            trailingContent = { Switch(checked = showScores, onCheckedChange = null) },
            modifier = Modifier.toggleable(showScores, role = Role.Switch, onValueChange = onScores)
        )
    }
    if (choosingMode) {
        AnalysisModeSheet(analysisMode, { choosingMode = false }) {
            choosingMode = false
            onMode(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisModeSheet(selected: AnalysisMode, onDismiss: () -> Unit, onMode: (AnalysisMode) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("分析模式", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
            AnalysisMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(mode.title) },
                    trailingContent = { RadioButton(selected == mode, onClick = null) },
                    modifier = Modifier.selectable(selected == mode, role = Role.RadioButton) { onMode(mode) }
                )
            }
        }
    }
}
