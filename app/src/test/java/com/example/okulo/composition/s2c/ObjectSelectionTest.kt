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
            listOf(1, 2, 3, 4, 5),
            objectIndices(boxes, floatArrayOf(0.8f, 0.9f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f))
        )
    }

    @Test(expected = IllegalStateException::class)
    fun insufficientObjectsCannotSilentlySupplyWrongNodeCount() {
        objectIndices(listOf(CropBox.FullFrame), floatArrayOf(1f))
    }
}
