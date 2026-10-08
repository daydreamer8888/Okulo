package com.example.okulo.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("okulo_settings", Context.MODE_PRIVATE)
    var showModelScores by mutableStateOf(preferences.getBoolean("model_scores", false))
        private set

    fun setModelScores(enabled: Boolean) {
        preferences.edit().putBoolean("model_scores", enabled).apply()
        showModelScores = enabled
    }
}
