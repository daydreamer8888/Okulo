package com.example.okulo.composition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CropGeometryTest {
    @Test
    fun candidatesKeepSourceRatioAndAtLeastHalfItsArea() {
        val boxes = cropCandidates()
        assertEquals(CropBox.FullFrame, boxes.first())
        assertEquals(251, boxes.size)
        for (box in boxes.drop(1)) {
            assertTrue(box.left >= 0 && box.top >= 0 && box.right <= 1 && box.bottom <= 1)
            assertTrue(box.area >= 0.5f - 1e-6f && box.area <= 0.95f + 1e-6f)
            for ((width, height) in listOf(533 to 498, 480 to 640, 2500 to 1707)) {
                val ratio = box.width * width / (box.height * height)
                assertEquals(width.toFloat() / height, ratio, 1e-5f)
            }
        }
    }

    @Test
    fun gridIncludesBothEdgesAndCenteredCropsAtEveryArea() {
        val boxes = cropCandidates().drop(1)
        for (level in boxes.chunked(25)) {
            assertEquals(0f, level.first().left, 0f)
            assertEquals(0f, level.first().top, 0f)
            assertEquals(1f, level.last().right, 0f)
            assertEquals(1f, level.last().bottom, 0f)
            assertEquals(0.5f, (level[12].left + level[12].right) / 2, 1e-6f)
            assertEquals(0.5f, (level[12].top + level[12].bottom) / 2, 1e-6f)
        }
    }

    @Test
    fun candidatesMatchDesktopReferenceOrder() {
        val boxes = cropCandidates()
        val reference = listOf(
            1 to floatArrayOf(0f, 0f, 0.70710677f, 0.70710677f),
            13 to floatArrayOf(0.14644661f, 0.14644661f, 0.8535534f, 0.8535534f),
            250 to floatArrayOf(0.025320565f, 0.025320565f, 1f, 1f)
        )
        for ((index, edges) in reference) {
            val box = boxes[index]
            val actual = listOf(box.left, box.top, box.right, box.bottom)
            assertTrue(actual.indices.all { abs(actual[it] - edges[it]) < 1e-6f })
        }
    }

    @Test
    fun nmsSuppressesOverlappingObjectsBeforeSelectingFive() {
        val boxes = listOf(
            CropBox(0f, 0f, 10f, 10f),
            CropBox(1f, 1f, 11f, 11f),
            CropBox(20f, 0f, 30f, 10f),
            CropBox(40f, 0f, 50f, 10f),
            CropBox(60f, 0f, 70f, 10f),
            CropBox(80f, 0f, 90f, 10f),
            CropBox(100f, 0f, 110f, 10f)
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            objectIndices(boxes, floatArrayOf(0.8f, 0.9f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f))
        )
    }

    @Test(expected = IllegalStateException::class)
    fun insufficientObjectsCannotSilentlySupplyWrongNodeCount() {
        objectIndices(listOf(CropBox.FullFrame), floatArrayOf(1f))
    }
}
