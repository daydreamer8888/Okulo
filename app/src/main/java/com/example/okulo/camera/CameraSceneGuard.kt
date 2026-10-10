package com.example.okulo.camera

import kotlin.math.abs

internal const val SCENE_GRID_SIZE = 8
private const val SCENE_CHANGE_THRESHOLD = 40.0
private const val SCENE_CHANGE_CONFIRMATIONS = 2

/** Compare coarse structure, ignoring exposure offsets and one-cell camera motion. */
internal class CameraSceneGuard {
    private var baseline: DoubleArray? = null
    private var changes = 0

    fun reset() {
        baseline = null
        changes = 0
    }

    fun changed(luminance: IntArray): Boolean {
        require(luminance.size == SCENE_GRID_SIZE * SCENE_GRID_SIZE)
        val mean = luminance.average()
        val current = DoubleArray(luminance.size) { luminance[it] - mean }
        val original = baseline
        if (original == null) {
            baseline = current
            return false
        }
        val difference = (-1..1).minOf { dx -> (-1..1).minOf { dy -> difference(original, current, dx, dy) } }
        changes = if (difference > SCENE_CHANGE_THRESHOLD) changes + 1 else 0
        return changes >= SCENE_CHANGE_CONFIRMATIONS
    }

    private fun difference(original: DoubleArray, current: DoubleArray, dx: Int, dy: Int): Double {
        var difference = 0.0
        var count = 0
        for (y in 0 until SCENE_GRID_SIZE) {
            for (x in 0 until SCENE_GRID_SIZE) {
                val otherX = x + dx
                val otherY = y + dy
                if (otherX in 0 until SCENE_GRID_SIZE && otherY in 0 until SCENE_GRID_SIZE) {
                    difference += abs(original[y * SCENE_GRID_SIZE + x] - current[otherY * SCENE_GRID_SIZE + otherX])
                    count++
                }
            }
        }
        return difference / count
    }
}
