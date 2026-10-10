package com.example.okulo.composition.s2c

import com.example.okulo.composition.CropBox
import com.example.okulo.composition.intersectionOverUnion

internal const val OBJECT_NODES = 5
private const val NMS_IOU = 0.5f

/** Keep distinct detections, then fill missing S2C nodes with global image context. */
internal fun objectRegions(boxes: List<CropBox>, confidence: FloatArray, image: CropBox): List<CropBox> {
    require(boxes.size == confidence.size)
    val kept = mutableListOf<Int>()
    for (index in confidence.indices.sortedByDescending { confidence[it] }) {
        if (kept.none { intersectionOverUnion(boxes[index], boxes[it]) > NMS_IOU }) kept.add(index)
        if (kept.size == OBJECT_NODES) break
    }
    return kept.map { boxes[it] } + List(OBJECT_NODES - kept.size) { image }
}
