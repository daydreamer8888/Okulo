package com.example.okulo.photo

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Only the latest photo, mode or crop request may publish state. */
internal class PhotoWorkQueue(
    private val state: MutableStateFlow<PhotoState>,
    private val release: () -> Unit
) : Closeable {
    private val epoch = RequestEpoch()
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    private var task: Future<*>? = null

    fun invalidate(): Long {
        val request = epoch.next()
        task?.cancel(false)
        worker.purge()
        return request
    }

    fun isCurrent(request: Long): Boolean = epoch.isCurrent(request)

    @Suppress("TooGenericExceptionCaught") // Report file/native failures at the asynchronous UI boundary.
    fun submit(request: Long, message: (Exception) -> String, work: () -> Unit) {
        task = worker.submit {
            try {
                work()
            } catch (_: CancellationException) {
                // The replacement request owns the screen.
            } catch (failure: Exception) {
                Log.e("OkuloAnalysis", "Photo task failed", failure)
                publish(request) { it.copy(busy = false, error = message(failure), status = "可以重试或选择其他照片") }
            }
        }
    }

    fun publish(request: Long, update: (PhotoState) -> PhotoState) {
        main.post { if (epoch.isCurrent(request)) state.value = update(state.value) }
    }

    override fun close() {
        invalidate()
        worker.execute(release)
        worker.shutdown()
    }
}
