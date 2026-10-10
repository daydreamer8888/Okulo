package com.example.okulo.camera

import androidx.camera.core.ZoomState
import androidx.concurrent.futures.CallbackToFutureAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraZoomTest {
    @Test
    fun requestsUseDeviceLimitsAndOnlyReportedZoomChangesTheDisplayedRatio() {
        val ratios = mutableListOf<Float>()
        var invalidations = 0
        val zoom = CameraZoom(
            setRatio = { ratio ->
                ratios += ratio
                CallbackToFutureAdapter.getFuture<Void> { it.set(null) }
            },
            setLinear = { error("No linear zoom expected") },
            executor = Executor { it.run() },
            onChanged = { invalidations++ }
        )
        zoom.requestRatio(2f)
        assertEquals(emptyList<Float>(), ratios)
        assertNull(zoom.state)
        zoom.update(DeviceZoom(1f, 3f, 1f, 0f))
        zoom.requestRatio(9f)
        assertEquals(listOf(3f), ratios)
        assertEquals(1f, zoom.state!!.zoomRatio, 0f)
        assertEquals(1, invalidations)
        zoom.update(DeviceZoom(1f, 3f, 3f, 1f))
        assertEquals(3f, zoom.state!!.zoomRatio, 0f)
        zoom.update(DeviceZoom(1f, 3f, 1.7f, 0.6f))
        assertEquals(1.7f, zoom.state!!.zoomRatio, 0f)
        assertEquals(3, invalidations)
        zoom.requestRatio(1.7f)
        zoom.requestRatio(Float.NaN)
        assertEquals(listOf(3f), ratios)
        zoom.update(null)
        zoom.requestRatio(2f)
        assertEquals(listOf(3f), ratios)
    }
}

internal data class DeviceZoom(
    private val minimum: Float,
    private val maximum: Float,
    private val ratio: Float,
    private val linear: Float
) : ZoomState {
    override fun getMinZoomRatio() = minimum
    override fun getMaxZoomRatio() = maximum
    override fun getZoomRatio() = ratio
    override fun getLinearZoom() = linear
}
