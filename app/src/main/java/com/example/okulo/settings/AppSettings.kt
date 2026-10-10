package com.example.okulo.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.okulo.composition.AnalysisMode

internal class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("okulo_settings", Context.MODE_PRIVATE)
    var cropWarningPercent by mutableStateOf(preferences.getInt("crop_warning_percent", DEFAULT_CROP_WARNING_PERCENT))
        private set

    fun updateCropWarningPercent(percent: Int) {
        require(
            percent in CROP_WARNING_MIN_PERCENT..CROP_WARNING_MAX_PERCENT && percent % CROP_WARNING_STEP_PERCENT == 0
        )
        preferences.edit().putInt("crop_warning_percent", percent).apply()
        cropWarningPercent = percent
    }

    var keepCaptureOriginal by mutableStateOf(preferences.getBoolean("capture_original", true))
        private set

    fun setCaptureOriginal(enabled: Boolean) {
        preferences.edit().putBoolean("capture_original", enabled).apply()
        keepCaptureOriginal = enabled
    }

    var showModelScores by mutableStateOf(preferences.getBoolean("model_scores", false))
        private set

    var analysisMode by mutableStateOf(
        AnalysisMode.entries.firstOrNull {
            it.name == preferences.getString("analysis_mode", null)
        } ?: AnalysisMode.Fast
    )
        private set

    var theme by mutableStateOf(
        AppTheme.entries.firstOrNull {
            it.name == preferences.getString("theme", null)
        } ?: AppTheme.System
    )
        private set

    var dynamicColor by mutableStateOf(preferences.getBoolean("dynamic_color", false))
        private set

    fun updateDynamicColor(enabled: Boolean) {
        preferences.edit().putBoolean("dynamic_color", enabled).apply()
        dynamicColor = enabled
    }

    fun selectTheme(theme: AppTheme) {
        preferences.edit().putString("theme", theme.name).apply()
        this.theme = theme
    }

    fun selectAnalysisMode(mode: AnalysisMode) {
        preferences.edit().putString("analysis_mode", mode.name).apply()
        analysisMode = mode
    }

    fun setModelScores(enabled: Boolean) {
        preferences.edit().putBoolean("model_scores", enabled).apply()
        showModelScores = enabled
    }
}

internal enum class AppTheme(val title: String) {
    System("跟随系统"), Light("浅色"), Dark("深色")
}

internal const val DEFAULT_CROP_WARNING_PERCENT = 50
internal const val CROP_WARNING_MIN_PERCENT = 10
internal const val CROP_WARNING_MAX_PERCENT = 90
internal const val CROP_WARNING_STEP_PERCENT = 10
