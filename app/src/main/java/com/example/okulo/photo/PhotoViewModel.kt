package com.example.okulo.photo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CompositionEngine
import com.example.okulo.composition.CropBox
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
                it.copy(result = result, busy = false, status = if (it.manualCrop == null) "分析完成" else "有新推荐")
            }
        }
    }

    fun updateCrop(box: CropBox) {
        if (state.value.result == null) return
        tasks.invalidate()
        mutableState.value = state.value.copy(
            manualCrop = box, manualScore = null, manualMillis = null,
            busy = false, error = null, status = "松手后重新评分"
        )
    }

    fun evaluateCrop() {
        val current = state.value
        val photo = current.photo ?: return
        val crop = current.manualCrop ?: return
        val request = tasks.invalidate()
        mutableState.value = current.copy(busy = true, status = "正在评价裁剪…", error = null)
        tasks.submit(request, ::analysisFailureMessage) {
            val result = engine.evaluate(photo, current.mode, crop) { tasks.isCurrent(request) }
            tasks.publish(request) {
                it.copy(
                    manualScore = result.cropScore,
                    manualMillis = result.analysisMillis,
                    busy = false,
                    status = "裁剪评分已更新"
                )
            }
        }
    }

    fun restoreRecommendation() {
        tasks.invalidate()
        mutableState.value = state.value.copy(
            manualCrop = null, manualScore = null, manualMillis = null,
            busy = false, error = null, status = "已恢复推荐"
        )
    }

    fun cancel() {
        tasks.invalidate()
        mutableState.value = state.value.copy(busy = false, status = "已取消分析")
    }

    fun retry() {
        val current = state.value
        when {
            current.photo == null -> selectedUri?.let(::selectPhoto)
            current.manualCrop != null -> evaluateCrop()
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
