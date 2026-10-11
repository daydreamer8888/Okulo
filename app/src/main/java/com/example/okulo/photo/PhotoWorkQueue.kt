package com.example.okulo.photo

import com.example.okulo.work.LatestWorkQueue
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.Closeable

internal class PhotoWorkQueue(
    private val state: MutableStateFlow<PhotoState>,
    release: () -> Unit
) : Closeable {
    private val queue = LatestWorkQueue(release)

    fun invalidate(): Long = queue.invalidate()
    fun isCurrent(request: Long): Boolean = queue.isCurrent(request)
    fun publish(request: Long, update: (PhotoState) -> PhotoState) {
        queue.publish(request) { state.value = update(state.value) }
    }
    fun submit(request: Long, message: (Exception) -> String, work: () -> Unit) {
        queue.submit(request, { failure ->
            state.value = state.value.copy(
                busy = false, scoring = false, error = message(failure), status = "可以重试或选择其他照片"
            )
        }, work)
    }

    override fun close() = queue.close()
}
