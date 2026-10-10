package com.example.okulo.composition

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

/** Serialize inference and resource release. Only the current request may publish state. */
internal class AnalysisWorkQueue<S>(
    private val state: MutableStateFlow<S>,
    private val release: () -> Unit
) : Closeable {
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    private var task: Future<*>? = null

    fun invalidate(): Long {
        val request = epoch.incrementAndGet()
        task?.cancel(false)
        worker.purge()
        return request
    }

    fun isCurrent(request: Long): Boolean = request == epoch.get()

    @Suppress("TooGenericExceptionCaught") // Report file/native failures at the asynchronous UI boundary.
    fun submit(request: Long, failureState: (S, Exception) -> S, work: () -> Unit) {
        task = worker.submit {
            try {
                work()
            } catch (_: CancellationException) {
                // The replacement request owns the screen.
            } catch (failure: Exception) {
                Log.e("OkuloAnalysis", "Analysis task failed", failure)
                publish(request) {
                    failureState(it, failure)
                }
            }
        }
    }

    fun publish(request: Long, update: (S) -> S) {
        main.post { if (isCurrent(request)) state.value = update(state.value) }
    }

    fun releaseAfterWork(release: () -> Unit) { worker.execute(release) }

    override fun close() {
        invalidate()
        worker.execute(release)
        worker.shutdown()
    }
}
