package com.example.okulo.composition.s2c

import com.example.okulo.composition.CropBox
import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectSelectionTest {
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
            listOf(boxes[1], boxes[2], boxes[3], boxes[4], boxes[5]),
            objectRegions(boxes, floatArrayOf(0.8f, 0.9f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f), CropBox.FullFrame)
        )
    }

    @Test
    fun oneSubjectRetainsItsRegionAndUsesImageContextForMissingNodes() {
        val subject = CropBox(40f, 20f, 100f, 80f)
        val image = CropBox(0f, 0f, 160f, 120f)
        assertEquals(
            listOf(subject, image, image, image, image),
            objectRegions(listOf(subject), floatArrayOf(0.9f), image)
        )
    }

    @Test
    fun sparseDetectionsKeepConfidenceOrderAndSuppressDuplicatesBeforePadding() {
        val first = CropBox(0f, 0f, 10f, 10f)
        val duplicate = CropBox(1f, 1f, 11f, 11f)
        val second = CropBox(20f, 0f, 30f, 10f)
        val image = CropBox(0f, 0f, 40f, 20f)
        assertEquals(
            listOf(second, first, image, image, image),
            objectRegions(listOf(first, duplicate, second), floatArrayOf(0.8f, 0.7f, 0.9f), image)
        )
    }

    @Test
    fun noDetectionsUsesFiveImageContextNodes() {
        val image = CropBox(0f, 0f, 160f, 224f)
        assertEquals(listOf(image, image, image, image, image), objectRegions(emptyList(), floatArrayOf(), image))
    }
}
