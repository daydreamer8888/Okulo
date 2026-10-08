package com.example.okulo.composition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CropEditingTest {
    @Test
    fun edgeResizingKeepsTheOppositeEdgeAndHonorsFreeOrLockedAspects() {
        val box = CropBox(0.2f, 0.3f, 0.8f, 0.7f)
        val free = listOf(
            CropBox(0.3f, 0.3f, 0.8f, 0.7f),
            CropBox(0.2f, 0.4f, 0.8f, 0.7f),
            CropBox(0.2f, 0.3f, 0.7f, 0.7f),
            CropBox(0.2f, 0.3f, 0.8f, 0.6f)
        )
        val deltas = listOf(0.1f, 0.1f, -0.1f, -0.1f)
        for ((index, edge) in CropEdge.entries.withIndex()) {
            val resized = resizeCropEdge(box, edge, deltas[index], lockedRatio = null)
            assertBox(free[index], resized)
            val locked = resizeCropEdge(box, edge, deltas[index], lockedRatio = 1.5f)
            assertEquals(1.5f, locked.width / locked.height, 1e-6f)
            if (edge == CropEdge.Left || edge == CropEdge.Right) {
                assertEquals(0.5f, locked.width, 1e-6f)
                assertEquals(1f, locked.top + locked.bottom, 1e-6f)
                assertEquals(resized.left, locked.left, 1e-6f)
                assertEquals(resized.right, locked.right, 1e-6f)
            } else {
                assertEquals(0.3f, locked.height, 1e-6f)
                assertEquals(1f, locked.left + locked.right, 1e-6f)
                assertEquals(resized.top, locked.top, 1e-6f)
                assertEquals(resized.bottom, locked.bottom, 1e-6f)
            }
            val bounded = resizeCropEdge(box, edge, -10f, lockedRatio = 1.5f)
            assertTrue(bounded.left >= 0f && bounded.top >= 0f && bounded.right <= 1f && bounded.bottom <= 1f)
            assertEquals(1.5f, bounded.width / bounded.height, 1e-5f)
            val minimal = resizeCropEdge(box, edge, 10f, lockedRatio = null)
            assertTrue(minimal.width >= MIN_EDIT_SIDE - 1e-6f && minimal.height >= MIN_EDIT_SIDE - 1e-6f)
        }
    }

    private fun assertBox(expected: CropBox, actual: CropBox) {
        assertEquals(expected.left, actual.left, 1e-6f)
        assertEquals(expected.top, actual.top, 1e-6f)
        assertEquals(expected.right, actual.right, 1e-6f)
        assertEquals(expected.bottom, actual.bottom, 1e-6f)
    }

    @Test
    fun movingAndResizingAnUnequalCropPreservesItsAspect() {
        val box = CropBox(0.1f, 0.2f, 0.9f, 0.6f)
        val zoomed = transformCrop(box, CropPoint(0f, 0f), 0.5f, CropPoint(0.5f, 0.4f), ratio = 2f)
        assertEquals(0.3f, zoomed.left, 1e-6f)
        assertEquals(0.3f, zoomed.top, 1e-6f)
        assertEquals(0.7f, zoomed.right, 1e-6f)
        assertEquals(0.5f, zoomed.bottom, 1e-6f)
        val resized = resizeCrop(box, CropCorner.BottomRight, -0.2f, -0.1f, lockedRatio = 2f)
        assertEquals(0.6f, resized.width, 1e-6f)
        assertEquals(0.3f, resized.height, 1e-6f)
        assertEquals(box.left, resized.left, 1e-6f)
        assertEquals(box.top, resized.top, 1e-6f)
    }

    @Test
    fun freeCornersResizeIndependentlyAndFixedRatiosFitInsideThePhoto() {
        val box = CropBox(0.1f, 0.2f, 0.9f, 0.6f)
        val free = resizeCrop(box, CropCorner.BottomRight, -0.2f, 0.2f, lockedRatio = null)
        assertEquals(0.6f, free.width, 1e-6f)
        assertEquals(0.6f, free.height, 1e-6f)
        assertEquals(0.1f, free.left, 1e-6f)
        assertEquals(0.2f, free.top, 1e-6f)
        val wide = fitCropAspect(CropBox.FullFrame, CropAspect.Wide.normalizedRatio(400, 300))
        assertEquals(0f, wide.left, 1e-6f)
        assertEquals(0.125f, wide.top, 1e-6f)
        assertEquals(1f, wide.right, 1e-6f)
        assertEquals(0.875f, wide.bottom, 1e-6f)
        assertEquals(box, fitCropAspect(box, CropAspect.Free.normalizedRatio(400, 300)))
    }

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
