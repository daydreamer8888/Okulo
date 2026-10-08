package com.example.okulo.photo

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CompositionAnalyzer
import com.example.okulo.composition.CropAspect
import com.example.okulo.composition.CropBox
import com.example.okulo.composition.createCompositionAnalyzer
import com.example.okulo.composition.fitCropAspect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Suppress("TooManyFunctions") // Public operations share one photo and request lifecycle.
class PhotoViewModel internal constructor(
    application: Application,
    private val engine: CompositionAnalyzer,
    private val photoReader: (ContentResolver, Uri) -> Bitmap
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, createCompositionAnalyzer(application), ::readPhoto)

    private val mutableState = MutableStateFlow(PhotoState())
    internal val state = mutableState.asStateFlow()
    private val tasks = PhotoWorkQueue(mutableState, engine::close)
    private var selectedUri: Uri? = null
    private val mutableSaving = MutableStateFlow(false)
    internal val saving = mutableSaving.asStateFlow()
    private val saveMessages = Channel<String>(Channel.BUFFERED)
    internal val saveEvents = saveMessages.receiveAsFlow()

    @Suppress("TooGenericExceptionCaught") // Storage errors become actionable save feedback.
    fun saveCrop() {
        val current = state.value
        val source = selectedUri
        val crop = current.displayedCrop
        if (source == null || crop == null) return
        if (mutableSaving.value || current.busy) return
        mutableSaving.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    CropExporter(getApplication<Application>()).save(source, current.transform, crop)
                }
                saveMessages.send("已保存到相册")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e("OkuloExport", "Crop save failed", failure)
                saveMessages.send("保存失败，请重试")
            } finally {
                mutableSaving.value = false
            }
        }
    }

    fun selectPhoto(uri: Uri) {
        selectedUri = uri
        val request = tasks.invalidate()
        mutableState.value = PhotoState(mode = state.value.mode, busy = true, status = "正在读取照片…")
        tasks.submit(request, { "照片读取失败，请重新选择" }) {
            val bitmap = photoReader(getApplication<Application>().contentResolver, uri)
            tasks.publish(request) { it.copy(photo = bitmap, busy = false, status = "照片已就绪") }
        }
    }

    fun setMode(mode: AnalysisMode) {
        if (state.value.mode == mode) return
        tasks.invalidate()
        val current = state.value
        mutableState.value = PhotoState(
            photo = current.photo, mode = mode, status = "照片已就绪", transform = current.transform,
            aspect = current.aspect, manualCrop = current.displayedCrop, freeRatio = current.freeRatio
        )
        if (current.photo == null) selectedUri?.let(::selectPhoto)
    }

    fun analyze() {
        val current = state.value
        val photo = current.photo ?: return
        val mode = current.mode
        val ratio = current.freeRatio ?: current.aspect.normalizedRatio(photo.width, photo.height)
        val request = tasks.invalidate()
        mutableState.value = state.value.copy(busy = true, scoring = false, status = "正在准备分析…", error = null)
        tasks.submit(request, ::analysisFailureMessage) {
            val result = engine.analyze(photo, mode, ratio, { tasks.isCurrent(request) }) { status ->
                tasks.publish(request) { it.copy(status = status) }
            }
            tasks.publish(request) {
                it.copy(
                    result = result,
                    manualCrop = null,
                    manualScore = null,
                    manualMillis = null,
                    busy = false,
                    scoring = false,
                    status = "分析完成"
                )
            }
        }
    }

    internal fun transformPhoto(operation: PhotoOperation) {
        val current = state.value
        val photo = current.photo ?: return
        val request = tasks.invalidate()
        mutableState.value = current.copy(busy = true, scoring = false, error = null)
        tasks.submit(request, { "无法编辑这张照片，请重试" }) {
            val step = PhotoTransform().followedBy(operation)
            val edited = step.apply(photo)
            tasks.publish(request) {
                PhotoState(
                    photo = edited,
                    mode = current.mode,
                    manualCrop = step.apply(current.displayedCrop ?: CropBox.FullFrame),
                    aspect = when (operation) {
                        PhotoOperation.RotateClockwise -> current.aspect.rotated()
                        else -> current.aspect
                    },
                    freeRatio = current.freeRatio?.let {
                        if (operation == PhotoOperation.RotateClockwise) 1f / it else it
                    },
                    transform = current.transform.followedBy(operation)
                )
            }
        }
    }

    fun setAspect(aspect: CropAspect) {
        val current = state.value
        val photo = current.photo ?: return
        if (current.aspect == aspect && current.freeRatio == null) return
        tasks.invalidate()
        mutableState.value = current.copy(
            aspect = aspect,
            freeRatio = null,
            manualCrop = current.displayedCrop?.let {
                fitCropAspect(it, aspect.normalizedRatio(photo.width, photo.height))
            },
            result = null,
            manualScore = null,
            manualOriginalScore = null,
            manualMillis = null,
            busy = false, scoring = false,
            error = null
        )
    }

    fun updateCrop(box: CropBox) {
        val current = state.value
        val previous = current.displayedCrop ?: return
        if (box == previous) return
        val ratio = box.width / box.height
        val previousRatio = previous.width / previous.height
        val reshaped = current.aspect == CropAspect.Free && abs(ratio / previousRatio - 1f) > ASPECT_TOLERANCE
        tasks.invalidate()
        mutableState.value = current.copy(
            manualCrop = box,
            freeRatio = if (reshaped) ratio else current.freeRatio,
            result = if (reshaped) null else current.result,
            manualScore = null,
            manualMillis = null,
            busy = false, scoring = false,
            error = null,
            status = "松手后重新评分"
        )
    }

    fun evaluateCrop() {
        val current = state.value
        val photo = current.photo ?: return
        val crop = current.manualCrop ?: return
        val request = tasks.invalidate()
        mutableState.value = current.copy(busy = false, scoring = true, status = "正在评价裁剪…", error = null)
        tasks.submit(request, ::analysisFailureMessage) {
            val result = engine.evaluate(photo, current.mode, crop) { tasks.isCurrent(request) }
            tasks.publish(request) {
                it.copy(
                    manualScore = result.cropScore,
                    manualOriginalScore = result.originalScore,
                    manualMillis = result.analysisMillis,
                    busy = false,
                    scoring = false,
                    status = "裁剪评分已更新"
                )
            }
        }
    }

    fun restoreRecommendation() {
        if (state.value.result == null) return
        tasks.invalidate()
        mutableState.value = state.value.copy(
            manualCrop = null, manualScore = null, manualMillis = null,
            busy = false, scoring = false, error = null, status = "已恢复推荐"
        )
    }

    fun cancel() {
        tasks.invalidate()
        mutableState.value = state.value.copy(busy = false, scoring = false, status = "已取消分析")
    }

    fun retry() {
        val current = state.value
        when {
            current.photo == null -> selectedUri?.let(::selectPhoto)
            current.result == null -> analyze()
            current.manualCrop != null -> evaluateCrop()
            else -> analyze()
        }
    }

    override fun onCleared() { tasks.close() }
}

private const val ASPECT_TOLERANCE = 1e-5f

private fun analysisFailureMessage(failure: Exception): String = when (failure) {
    is IllegalStateException -> failure.message ?: "分析失败，请重试"
    is java.io.IOException -> "无法准备本地模型，请检查存储空间后重试"
    else -> "模型未能完成这张照片的分析，请重试或换张照片"
}
