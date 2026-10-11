package com.example.okulo.work

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Serialize work and resource release. Only the current request may publish a result or failure. */
internal class LatestWorkQueue(private val release: () -> Unit) : Closeable {
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
    fun submit(request: Long, onFailure: (Exception) -> Unit, work: () -> Unit) {
        task = worker.submit {
            try {
                work()
            } catch (_: CancellationException) {
                // The replacement request owns the screen.
            } catch (failure: Exception) {
                Log.e("OkuloWork", "Background task failed", failure)
                publish(request) {
                    onFailure(failure)
                }
            }
        }
    }

    fun publish(request: Long, update: () -> Unit) {
        main.post { if (isCurrent(request)) update() }
    }

    fun releaseAfterWork(release: () -> Unit) { worker.execute(release) }

    override fun close() {
        invalidate()
        worker.execute(release)
        worker.shutdown()
    }
}
