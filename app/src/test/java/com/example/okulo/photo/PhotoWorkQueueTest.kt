package com.example.okulo.photo

import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoWorkQueueTest {
    @Test
    fun invalidationRejectsAnAlreadyPostedResult() {
        val state = MutableStateFlow(PhotoState(status = "current"))
        PhotoWorkQueue(state, {}).use { queue ->
            val request = queue.invalidate()
            queue.publish(request) { it.copy(status = "obsolete") }
            queue.invalidate()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("current", state.value.status)
        }
    }

    @Test
    fun latestRequestReplacesQueuedWorkWithoutInterruptingActiveInference() {
        val state = MutableStateFlow(PhotoState())
        val gate = WorkGate()
        val obsoleteCalls = AtomicInteger()
        val finished = CountDownLatch(1)
        PhotoWorkQueue(state, {}).use { queue ->
            try {
                val first = queue.invalidate()
                queue.submit(first, { "failure" }) {
                    gate.block()
                    queue.publish(first) { it.copy(status = "first") }
                }
                gate.awaitEntry()
                val obsolete = queue.invalidate()
                queue.submit(obsolete, { "failure" }) { obsoleteCalls.incrementAndGet() }
                val latest = queue.invalidate()
                queue.submit(latest, { "failure" }) {
                    queue.publish(latest) { it.copy(status = "latest") }
                    finished.countDown()
                }
                gate.release()
                assertTrue(finished.await(10, TimeUnit.SECONDS))
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(0, obsoleteCalls.get())
                assertEquals("latest", state.value.status)
            } finally {
                gate.release()
            }
        }
    }

    @Test
    fun currentFailureIsReportedAndCancellationPreservesCurrentState() {
        val state = MutableStateFlow(PhotoState(busy = true))
        val released = CountDownLatch(1)
        val queue = PhotoWorkQueue(state, released::countDown)
        try {
            val request = queue.invalidate()
            queue.submit(request, { "read denied" }) { throw IOException("permission denied") }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (state.value.busy) {
                shadowOf(Looper.getMainLooper()).idle()
                check(System.nanoTime() < deadline)
                Thread.yield()
            }
            assertEquals("read denied", state.value.error)
            val cancelled = queue.invalidate()
            val cancelledFinished = CountDownLatch(1)
            queue.submit(cancelled, { "unexpected failure" }) { throw CancellationException() }
            queue.submit(cancelled, { "unexpected failure" }) { cancelledFinished.countDown() }
            assertTrue(cancelledFinished.await(10, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("read denied", state.value.error)
        } finally {
            queue.close()
        }
        assertTrue(released.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun closeReleasesResourcesAfterRunningInferenceOnItsWorker() {
        val gate = WorkGate()
        val released = CountDownLatch(1)
        var inferenceThread: Thread? = null
        var releaseThread: Thread? = null
        val queue = PhotoWorkQueue(MutableStateFlow(PhotoState())) {
            releaseThread = Thread.currentThread()
            released.countDown()
        }
        try {
            queue.submit(queue.invalidate(), { "failure" }) {
                inferenceThread = Thread.currentThread()
                gate.block()
            }
            gate.awaitEntry()
            queue.close()
            assertEquals(1L, released.count)
            gate.release()
            assertTrue(released.await(10, TimeUnit.SECONDS))
            assertEquals(inferenceThread, releaseThread)
            assertTrue(releaseThread != Thread.currentThread())
        } finally {
            gate.release()
        }
    }
}
