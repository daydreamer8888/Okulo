package com.example.okulo.camera

import android.graphics.PointF
import android.util.Range
import android.util.Rational
import androidx.camera.core.ExposureState
import androidx.camera.view.CameraController
import androidx.camera.view.TapToFocusInfo
import androidx.concurrent.futures.CallbackToFutureAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraMeteringTest {
    @Test
    fun exposureUsesDeviceLimitsAndIgnoresFailuresFromSupersededOrClosedRequests() {
        val values = mutableListOf<Int>()
        val pending = mutableListOf<CallbackToFutureAdapter.Completer<Int>>()
        var failures = 0
        val metering = CameraMetering(
            exposureState = { DeviceExposure() },
            setExposure = { value ->
                values += value
                CallbackToFutureAdapter.getFuture<Int> {
                    pending += it
                    "exposure"
                }
            },
            executor = Executor { it.run() },
            onFailure = { failures++ }
        )
        metering.requestExposure(2f)
        assertEquals(emptyList<Int>(), values)
        metering.updateFocus(TapToFocusInfo(CameraController.TAP_TO_FOCUS_STARTED, PointF(40f, 80f)))
        assertEquals(-2..2, metering.state.range)
        metering.requestExposure(20f)
        metering.requestExposure(-1f)
        metering.requestExposure(Float.NaN)
        assertEquals(listOf(2, -1), values)
        pending[0].setException(IOException("superseded"))
        assertEquals(-1, metering.state.index)
        assertEquals(0, failures)
        pending[1].setException(IOException("device failure"))
        assertEquals(0, metering.state.index)
        assertEquals(1, failures)
        metering.requestExposure(1f)
        pending[2].set(1)
        assertEquals(1, metering.state.index)
        metering.requestExposure(-2f)
        metering.reset()
        pending[3].setException(IOException("camera closed"))
        assertNull(metering.state.focus)
        assertEquals(1, failures)
    }
}

internal class DeviceExposure(private val supported: Boolean = true) : ExposureState {
    override fun isExposureCompensationSupported() = supported
    override fun getExposureCompensationIndex() = 0
    override fun getExposureCompensationRange() = Range(-2, 2)
    override fun getExposureCompensationStep() = Rational(1, 3)
}
