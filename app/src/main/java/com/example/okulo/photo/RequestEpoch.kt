package com.example.okulo.photo

import java.util.concurrent.atomic.AtomicLong

/** A result can update the screen only while its photo and mode are still current. */
internal class RequestEpoch {
    private val value = AtomicLong()
    fun next(): Long = value.incrementAndGet()
    fun isCurrent(request: Long): Boolean = request == value.get()
}
