package com.example.okulo.photo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CompositionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(PhotoState())
    internal val state = mutableState.asStateFlow()
    private val engine = CompositionEngine(application)
    private val tasks = PhotoWorkQueue(mutableState, engine::close)
    private var selectedUri: Uri? = null

    fun selectPhoto(uri: Uri) {
        selectedUri = uri
        val request = tasks.invalidate()
        mutableState.value = PhotoState(mode = state.value.mode, busy = true, status = "正在读取照片…")
        tasks.submit(request, { "照片读取失败，请重新选择" }) {
            val bitmap = readPhoto(getApplication<Application>().contentResolver, uri)
            tasks.publish(request) { it.copy(photo = bitmap, busy = false, status = "照片已就绪") }
        }
    }

    fun setMode(mode: AnalysisMode) {
        if (state.value.mode == mode) return
        tasks.invalidate()
        val current = state.value
        mutableState.value = PhotoState(photo = current.photo, mode = mode, status = "照片已就绪")
        if (current.photo == null) selectedUri?.let(::selectPhoto)
    }

    fun analyze() {
        val photo = state.value.photo ?: return
        val mode = state.value.mode
        val request = tasks.invalidate()
        mutableState.value = state.value.copy(busy = true, status = "正在准备分析…", error = null)
        tasks.submit(request, ::analysisFailureMessage) {
            val result = engine.analyze(photo, mode, { tasks.isCurrent(request) }) { status ->
                tasks.publish(request) { it.copy(status = status) }
            }
            tasks.publish(request) {
                it.copy(result = result, busy = false, status = "分析完成")
            }
        }
    }

    fun cancel() {
        tasks.invalidate()
        mutableState.value = state.value.copy(busy = false, status = "已取消分析")
    }

    fun retry() {
        val current = state.value
        when {
            current.photo == null -> selectedUri?.let(::selectPhoto)
            else -> analyze()
        }
    }

    override fun onCleared() { tasks.close() }
}

private fun analysisFailureMessage(failure: Exception): String = when (failure) {
    is IllegalStateException -> failure.message ?: "分析失败，请重试"
    is java.io.IOException -> "无法准备本地模型，请检查存储空间后重试"
    else -> "模型未能完成这张照片的分析，请重试或换张照片"
}
