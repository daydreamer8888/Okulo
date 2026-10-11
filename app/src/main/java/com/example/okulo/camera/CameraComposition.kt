package com.example.okulo.camera

import android.graphics.Bitmap
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CompositionAnalyzer
import com.example.okulo.composition.CompositionResult
import com.example.okulo.composition.CropBox
import com.example.okulo.composition.CropSearch
import com.example.okulo.composition.DEFAULT_MINIMUM_CROP_AREA
import com.example.okulo.work.LatestWorkQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

internal data class CameraCompositionState(
    val recommendation: CompositionResult? = null,
    val crop: CropBox? = null,
    val originalScore: Float? = null,
    val cropScore: Float? = null,
    val busy: Boolean = false,
    val scoring: Boolean = false,
    val error: String? = null
) {
    val canRestore: Boolean get() = recommendation != null && crop != recommendation.crop
    val active: Boolean get() = busy || crop != null
}

/** Owns analysis snapshots without replacing or pausing the live viewfinder. */
@Suppress("TooManyFunctions") // Operations share the camera scene and its inference lifecycle.
internal class CameraComposition(private val analyzer: CompositionAnalyzer) : Closeable {
    private val mutableState = MutableStateFlow(CameraCompositionState())
    val state = mutableState.asStateFlow()
    private val tasks = LatestWorkQueue(analyzer::close)
    private var frame: Bitmap? = null
    private val scene = CameraSceneGuard()

    /** Transfers ownership of the snapshot to this session. */
    fun recommend(snapshot: Bitmap, mode: AnalysisMode, minimumArea: Float = DEFAULT_MINIMUM_CROP_AREA) {
        scene.reset()
        val request = tasks.invalidate()
        releaseFrame()
        frame = snapshot
        mutableState.value = state.value.copy(busy = true, scoring = false, error = null)
        tasks.submit(request, { _ ->
            mutableState.value = state.value.copy(busy = false, scoring = false, error = "推荐失败，请重试")
        }) {
            val result = analyzer.analyze(
                snapshot,
                mode,
                CropSearch(null, minimumArea),
                { tasks.isCurrent(request) },
                {}
            )
            tasks.publish(request) {
                mutableState.value = CameraCompositionState(
                    recommendation = result,
                    crop = result.crop,
                    originalScore = result.originalScore,
                    cropScore = result.cropScore
                )
            }
        }
    }

    fun updateCrop(crop: CropBox) {
        val current = state.value
        if (current.crop == null || current.crop == crop) return
        tasks.invalidate()
        mutableState.value = current.copy(crop = crop, cropScore = null, busy = false, scoring = true, error = null)
    }

    fun evaluateCrop(snapshot: Bitmap, mode: AnalysisMode) {
        val current = state.value
        val crop = current.crop
        if (crop == null) {
            snapshot.recycle()
            return
        }
        val request = tasks.invalidate()
        releaseFrame()
        frame = snapshot
        mutableState.value = current.copy(busy = false, scoring = true, error = null)
        tasks.submit(request, { _ ->
            mutableState.value = state.value.copy(scoring = false, error = "评分失败，请重试")
        }) {
            val result = analyzer.evaluate(snapshot, mode, crop) { tasks.isCurrent(request) }
            tasks.publish(request) {
                mutableState.value = state.value.copy(
                    originalScore = result.originalScore, cropScore = result.cropScore, scoring = false
                )
            }
        }
    }

    fun restore() {
        val result = state.value.recommendation ?: return
        tasks.invalidate()
        mutableState.value = state.value.copy(
            crop = result.crop, originalScore = result.originalScore, cropScore = result.cropScore,
            busy = false, scoring = false, error = null
        )
    }

    fun dismissError() { mutableState.value = state.value.copy(error = null) }

    fun previewUnavailable() {
        tasks.invalidate()
        mutableState.value = state.value.copy(busy = false, scoring = false, error = "取景尚未就绪，请重试")
    }

    fun dismiss() {
        val current = state.value
        tasks.invalidate()
        releaseFrame()
        mutableState.value = if (current.busy) {
            current.copy(busy = false, scoring = false, error = null)
        } else {
            CameraCompositionState()
        }
    }

    fun observeScene(samples: IntArray) {
        if (state.value.active && scene.changed(samples)) invalidateScene()
    }

    fun invalidateScene() {
        scene.reset()
        tasks.invalidate()
        releaseFrame()
        mutableState.value = CameraCompositionState()
    }

    private fun releaseFrame() {
        frame?.let { previous -> tasks.releaseAfterWork { previous.recycle() } }
        frame = null
    }

    override fun close() {
        tasks.invalidate()
        releaseFrame()
        tasks.close()
    }
}
