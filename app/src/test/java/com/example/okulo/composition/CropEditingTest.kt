package com.example.okulo.composition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CropEditingTest {
    @Test
    fun movingAndZoomingKeepRatioAndStayInsidePhoto() {
        val random = Random(42)
        var crop = CropBox(0.1f, 0.1f, 0.9f, 0.9f)
        repeat(1000) {
            crop = transformCrop(
                crop, CropPoint(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f),
                0.5f + random.nextFloat(), CropPoint(random.nextFloat(), random.nextFloat())
            )
            assertEquals(crop.width, crop.height, 1e-6f)
            assertTrue(crop.left >= 0f && crop.top >= 0f && crop.right <= 1f && crop.bottom <= 1f)
            assertTrue(crop.width >= MIN_EDIT_SIDE - 1e-6f)
        }
    }

    @Test
    fun cornerResizeKeepsOppositeCornerFixedAndAllowsSmallManualCrop() {
        val box = CropBox(0.1f, 0.1f, 0.9f, 0.9f)
        for (corner in CropCorner.entries) {
            val crop = resizeCrop(box, corner, -0.4f * corner.horizontal, -0.4f * corner.vertical)
            assertEquals(0.4f, crop.width, 1e-6f)
            assertEquals(crop.width, crop.height, 1e-6f)
            assertEquals(
                if (corner.horizontal > 0) box.left else box.right,
                if (corner.horizontal > 0) crop.left else crop.right,
                1e-6f
            )
            assertEquals(
                if (corner.vertical > 0) box.top else box.bottom,
                if (corner.vertical > 0) crop.top else crop.bottom,
                1e-6f
            )
            assertTrue(crop.area < 0.5f)
        }
    }

    @Test
    fun zoomKeepsFocusPositionAndClampsExtremeSizes() {
        val box = CropBox(0.2f, 0.2f, 0.8f, 0.8f)
        val crop = transformCrop(box, CropPoint(0f, 0f), 0.5f, CropPoint(0.5f, 0.5f))
        assertEquals(0.35f, crop.left, 1e-6f)
        assertEquals(0.65f, crop.right, 1e-6f)
        assertEquals(CropBox.FullFrame, transformCrop(box, CropPoint(1f, -1f), 100f, CropPoint(0.5f, 0.5f)))
        assertEquals(MIN_EDIT_SIDE, transformCrop(box, CropPoint(0f, 0f), 0f, CropPoint(0.5f, 0.5f)).width, 1e-6f)
    }
}
