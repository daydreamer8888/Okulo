package com.example.okulo.composition.s2c

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.okulo.composition.CropBox
import java.nio.FloatBuffer
import kotlin.math.floor
import kotlin.math.round

/** Returns distinct regions in source-image coordinates, including global context when needed. */
internal class ObjectDetector(environment: OrtEnvironment, assets: ModelAssets) : AutoCloseable {
    private val model = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        options.setInterOpNumThreads(1)
        environment.createSession(assets.file("yolo26n.onnx").absolutePath, options)
    }
    private val environment = environment

    fun regions(inputs: ImageTensors): List<CropBox> {
        val width = inputs.rawShape[WIDTH_DIM].toInt()
        val height = inputs.rawShape[HEIGHT_DIM].toInt()
        val layout = Letterbox(width, height)
        val pixels = layout.pixels(inputs.raw)
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(pixels),
            longArrayOf(1, CHANNELS.toLong(), INPUT_SIZE, INPUT_SIZE)
        )
            .use { tensor ->
                model.run(mapOf("images" to tensor)).use { output ->
                    return decode(
                        (output[0] as OnnxTensor).floatBuffer,
                        layout,
                        CropBox(0f, 0f, width.toFloat(), height.toFloat())
                    )
                }
            }
    }

    private fun decode(values: FloatBuffer, layout: Letterbox, image: CropBox): List<CropBox> {
        val boxes = mutableListOf<CropBox>()
        val confidence = mutableListOf<Float>()
        while (values.hasRemaining()) {
            val box = layout.sourceBox(values.get(), values.get(), values.get(), values.get())
            val score = values.get()
            values.get() // Class id. S2C uses class-independent regions.
            val validScore = score.isFinite() && score >= MIN_CONFIDENCE
            if (validScore && box.width > 0 && box.height > 0) {
                boxes.add(box)
                confidence.add(score)
            }
        }
        return objectRegions(boxes, confidence.toFloatArray(), image)
    }

    override fun close() = model.close()
}

private class Letterbox(private val width: Int, private val height: Int) {
    private val gain = INPUT_SIZE.toDouble() / maxOf(width, height)
    private val resizedWidth = round(width * gain).toInt().coerceAtLeast(1)
    private val resizedHeight = round(height * gain).toInt().coerceAtLeast(1)
    private val left = ((INPUT_SIZE - resizedWidth) / 2).toInt()
    private val top = ((INPUT_SIZE - resizedHeight) / 2).toInt()

    fun pixels(raw: FloatArray): FloatArray {
        val output = FloatArray((CHANNELS * INPUT_SIZE * INPUT_SIZE).toInt()) { PADDING }
        for (channel in 0 until CHANNELS) {
            val sourceOffset = channel * width * height
            val targetOffset = channel * INPUT_SIZE.toInt() * INPUT_SIZE.toInt()
            for (y in 0 until resizedHeight) {
                val sy = ((y + HALF_PIXEL) * height / resizedHeight - HALF_PIXEL).coerceIn(0.0, height - 1.0)
                val y0 = floor(sy).toInt()
                val y1 = minOf(y0 + 1, height - 1)
                for (x in 0 until resizedWidth) {
                    val sx = ((x + HALF_PIXEL) * width / resizedWidth - HALF_PIXEL).coerceIn(0.0, width - 1.0)
                    val x0 = floor(sx).toInt()
                    val x1 = minOf(x0 + 1, width - 1)
                    val upper =
                        interpolate(raw[sourceOffset + y0 * width + x0], raw[sourceOffset + y0 * width + x1], sx - x0)
                    val lower =
                        interpolate(raw[sourceOffset + y1 * width + x0], raw[sourceOffset + y1 * width + x1], sx - x0)
                    val value = interpolate(upper, lower, sy - y0)
                    output[targetOffset + (y + top) * INPUT_SIZE.toInt() + x + left] =
                        (round(value * RGB_MAX) / RGB_MAX).toFloat()
                }
            }
        }
        return output
    }

    fun sourceBox(x0: Float, y0: Float, x1: Float, y1: Float) = CropBox(
        ((x0 - left) / gain).toFloat().coerceIn(0f, width.toFloat()),
        ((y0 - top) / gain).toFloat().coerceIn(0f, height.toFloat()),
        ((x1 - left) / gain).toFloat().coerceIn(0f, width.toFloat()),
        ((y1 - top) / gain).toFloat().coerceIn(0f, height.toFloat())
    )
}

private fun interpolate(first: Float, second: Float, weight: Double): Float =
    (first * (1 - weight) + second * weight).toFloat()

private const val INPUT_SIZE = 320L
private const val CHANNELS = 3
private const val WIDTH_DIM = 3
private const val HEIGHT_DIM = 2
private const val MIN_CONFIDENCE = 0.05f
private const val RGB_MAX = 255.0
private const val PADDING = 114f / 255f
private const val HALF_PIXEL = 0.5
