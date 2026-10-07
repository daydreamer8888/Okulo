package com.example.okulo.composition.s2c

import com.example.okulo.composition.CropBox
import com.example.okulo.composition.intersectionOverUnion

internal const val OBJECT_NODES = 5
private const val NMS_IOU = 0.5f

/** Class-agnostic NMS supplies the five spatial nodes expected by S2C. */
internal fun objectIndices(boxes: List<CropBox>, confidence: FloatArray): List<Int> {
    require(boxes.size == confidence.size)
    val kept = mutableListOf<Int>()
    for (index in confidence.indices.sortedByDescending { confidence[it] }) {
        if (kept.none { intersectionOverUnion(boxes[index], boxes[it]) > NMS_IOU }) kept.add(index)
        if (kept.size == OBJECT_NODES) break
    }
    check(kept.size == OBJECT_NODES) { "检测区域不足，无法完成本次分析" }
    return kept
}
