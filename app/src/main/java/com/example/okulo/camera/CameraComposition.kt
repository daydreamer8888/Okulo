package com.example.okulo.camera

import android.graphics.Bitmap
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.AnalysisWorkQueue
import com.example.okulo.composition.CompositionAnalyzer
import com.example.okulo.composition.CompositionResult
import com.example.okulo.composition.CropBox
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
internal class CameraComposition(private val analyzer: CompositionAnalyzer) : Closeable {
    private val mutableState = MutableStateFlow(CameraCompositionState())
    val state = mutableState.asStateFlow()
    private val tasks = AnalysisWorkQueue(mutableState, analyzer::close)
    private var frame: Bitmap? = null

    /** Transfers ownership of the snapshot to this session. */
    fun recommend(snapshot: Bitmap, mode: AnalysisMode) {
        val request = tasks.invalidate()
        releaseFrame()
        frame = snapshot
        mutableState.value = state.value.copy(busy = true, scoring = false, error = null)
        tasks.submit(request, { current, _ ->
            current.copy(busy = false, scoring = false, error = "推荐失败，请重试")
        }) {
            val result = analyzer.analyze(snapshot, mode, null, { tasks.isCurrent(request) }) {}
            tasks.publish(request) {
                CameraCompositionState(
                    recommendation = result,
                    crop = result.crop,
                    originalScore = result.originalScore,
                    cropScore = result.cropScore
                )
            }
        }
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
