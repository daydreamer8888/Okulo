package com.example.okulo.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.os.Looper
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import androidx.camera.view.LifecycleCameraController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraHighlightsTest {
    @Test
    fun highlightRegionsFollowTheCropAndAllCameraRotationsWithPaddedBuffers() {
        val controller = LifecycleCameraController(RuntimeEnvironment.getApplication())
        var now = 0L
        var published: HighlightRegions? = null
        val stream = CameraSceneStream(
            controller,
            highlightsEnabled = { true },
            onHighlights = { published = it },
            clock = { now },
            onScene = {}
        )
        try {
            val cases = listOf(
                Triple(0, 4 to 2, Rect(0, 0, 2, 2)),
                Triple(90, 2 to 4, Rect(0, 0, 2, 2)),
                Triple(180, 4 to 2, Rect(2, 0, 4, 2)),
                Triple(270, 2 to 4, Rect(0, 2, 2, 4))
            )
            for ((rotation, dimensions, expected) in cases) {
                val frame = LumaFrame(rotation).apply { cropRect = Rect(2, 1, 6, 3) }
                stream.analyze(frame)
                shadowOf(Looper.getMainLooper()).idle()
                val mask = checkNotNull(published)
                assertMask(mask, dimensions, expected, rotation)
                assertEquals(3, frame.buffer.position())
                assertTrue(frame.closed)
                now += 120
            }
        } finally {
            stream.close()
        }
    }

    private fun assertMask(mask: HighlightRegions, dimensions: Pair<Int, Int>, expected: Rect, rotation: Int) {
        assertEquals(dimensions.first, mask.width)
        assertEquals(dimensions.second, mask.height)
        for (index in 0 until dimensions.first * dimensions.second) {
            val x = index % dimensions.first
            val y = index / dimensions.first
            assertEquals(
                "$rotation degrees at $x,$y",
                expected.contains(x, y),
                mask.regions.any { it.contains(x, y) }
            )
        }
    }

    @Test
    fun disablingHighlightsKeepsSceneSamplingAndDropsQueuedOrClosedResults() {
        val controller = LifecycleCameraController(RuntimeEnvironment.getApplication())
        var now = 0L
        var enabled = false
        val masks = mutableListOf<HighlightRegions?>()
        var scenes = 0
        val stream = CameraSceneStream(
            controller,
            highlightsEnabled = { enabled },
            onHighlights = { masks += it },
            clock = { now },
            onScene = { scenes++ }
        )
        stream.analyze(LumaFrame(0))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(masks.isEmpty())
        assertEquals(1, scenes)
        enabled = true
        now += 120
        stream.analyze(LumaFrame(0))
        enabled = false
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(masks.isEmpty())
        enabled = true
        now += 120
        stream.analyze(LumaFrame(0, peak = 234))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, masks.size)
        assertNull(masks.single())
        now += 120
        stream.analyze(LumaFrame(0))
        stream.close()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, masks.size)
        val late = LumaFrame(0)
        stream.analyze(late)
        assertTrue(late.closed)
        assertFalse(late.sampled)
    }
}

/** Hardware frame fixture: bright padding must never become part of the visible mask. */
internal class LumaFrame(private val rotation: Int, peak: Int = 235) : ImageProxy {
    val buffer: ByteBuffer = ByteBuffer.allocate(3 + 20 * 6).apply {
        for (index in 0 until capacity()) put(index, 255.toByte())
        for (y in 0..5) for (x in 0..7) {
            put(3 + y * 20 + x * 2, (if (x in 2..3 && y in 1..2) peak else 100).toByte())
        }
        position(3)
    }
    var closed = false
    var sampled = false
    private var crop = Rect(2, 1, 6, 5)
    override fun close() { closed = true }
    override fun getCropRect() = crop
    override fun setCropRect(rect: Rect?) { crop = rect ?: Rect(0, 0, 8, 6) }
    override fun getFormat() = ImageFormat.YUV_420_888
    override fun getHeight() = 6
    override fun getWidth() = 8
    override fun getImage(): Image? = null
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> {
        sampled = true
        return arrayOf(object : ImageProxy.PlaneProxy {
            override fun getBuffer(): ByteBuffer = this@LumaFrame.buffer
            override fun getRowStride() = 20
            override fun getPixelStride() = 2
        })
    }
    override fun getImageInfo() = object : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
        override fun getTimestamp() = 0L
        override fun getRotationDegrees() = rotation
        override fun populateExifData(builder: ExifData.Builder) = Unit
    }
}
