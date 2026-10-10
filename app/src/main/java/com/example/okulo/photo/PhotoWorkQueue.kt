package com.example.okulo.photo

import com.example.okulo.composition.AnalysisWorkQueue
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.Closeable

internal class PhotoWorkQueue(state: MutableStateFlow<PhotoState>, release: () -> Unit) : Closeable {
    private val queue = AnalysisWorkQueue(state, release)

    fun invalidate(): Long = queue.invalidate()
    fun isCurrent(request: Long): Boolean = queue.isCurrent(request)
    fun publish(request: Long, update: (PhotoState) -> PhotoState) = queue.publish(request, update)
    fun submit(request: Long, message: (Exception) -> String, work: () -> Unit) {
        queue.submit(request, { current, failure ->
            current.copy(busy = false, scoring = false, error = message(failure), status = "可以重试或选择其他照片")
        }, work)
    }

    override fun close() = queue.close()
}
