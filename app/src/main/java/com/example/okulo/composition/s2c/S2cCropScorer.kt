package com.example.okulo.composition.s2c

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.example.okulo.composition.CropBox
import com.example.okulo.composition.CropScoringModel
import java.nio.FloatBuffer
import java.util.concurrent.CancellationException
import kotlin.math.ceil
import kotlin.math.floor

internal class S2cCropScorer(context: Context) : CropScoringModel {
    private val assets = ModelAssets(context)
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private var scorer: OrtSession? = null
    private var detector: ObjectDetector? = null
    private var cached: CachedPhoto? = null

    override fun score(
        bitmap: Bitmap,

        candidates: List<CropBox>,
        isCurrent: () -> Boolean
    ): FloatArray = scoreCrops(prepare(bitmap, isCurrent), candidates)

    override fun load() {
        if (scorer == null) scorer = session("s2c.onnx")
        if (detector == null) detector = ObjectDetector(environment, assets)
    }

    private fun session(name: String): OrtSession = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        options.setInterOpNumThreads(1)
        environment.createSession(assets.file(name).absolutePath, options)
    }

    private fun prepare(bitmap: Bitmap, isCurrent: () -> Boolean): ScoringImage {
        val previous = cached
        if (previous != null && previous.bitmap === bitmap) return previous.image
        val inputs = imageTensors(bitmap)
        checkCurrent(isCurrent)
        val image = detectionInputs(checkNotNull(detector), inputs)
        checkCurrent(isCurrent)
        cached = CachedPhoto(bitmap, image)
        return image
    }

    internal fun scoreInputs(
        inputs: ImageTensors,
        candidates: List<CropBox>,
        isCurrent: () -> Boolean = { true }
    ): FloatArray {
        val image = detectionInputs(checkNotNull(detector), inputs)
        checkCurrent(isCurrent)
        return scoreCrops(image, candidates)
    }

    private fun scoreCrops(image: ScoringImage, candidates: List<CropBox>): FloatArray {
        val width = image.shape[WIDTH_DIM].toInt()
        val height = image.shape[HEIGHT_DIM].toInt()
        val boxes = candidates.flatMap { box ->
            listOf(0f, box.left * width, box.top * height, box.right * width, box.bottom * height)
        }.toFloatArray()
        tensor(environment, image.values, image.shape).use { pixels ->
            tensor(environment, boxes, longArrayOf(candidates.size.toLong(), ROI_FIELDS)).use { crops ->
                tensor(environment, image.objects, longArrayOf(OBJECT_NODES.toLong(), ROI_FIELDS)).use { objects ->
                    return runScorer(
                        mapOf("image" to pixels, "crop_boxes" to crops, "det_boxes" to objects),
                        candidates.size
                    )
                }
            }
        }
    }

    private fun runScorer(feed: Map<String, OnnxTensor>, count: Int): FloatArray =
        checkNotNull(scorer).run(feed).use { output ->
            val values = (output[0] as OnnxTensor).floatBuffer
            FloatArray(values.remaining()).also { scores ->
                values.get(scores)
                check(scores.size == count && scores.all { it.isFinite() }) { "模型评分异常，请重试" }
            }
        }

    override fun close() {
        detector?.close()
        scorer?.close()
        detector = null
        scorer = null
        cached = null
    }
}

private data class ScoringImage(val values: FloatArray, val shape: LongArray, val objects: FloatArray)
private data class CachedPhoto(val bitmap: Bitmap, val image: ScoringImage)

private fun detectionInputs(detector: ObjectDetector, inputs: ImageTensors): ScoringImage {
    val sx = inputs.normalizedShape[WIDTH_DIM].toDouble() / inputs.rawShape[WIDTH_DIM]
    val sy = inputs.normalizedShape[HEIGHT_DIM].toDouble() / inputs.rawShape[HEIGHT_DIM]
    val objects = detector.regions(inputs).flatMap { box ->
        listOf(
            0f,
            floor(box.left * sx).toFloat(),
            floor(box.top * sy).toFloat(),
            ceil(box.right * sx).toFloat(),
            ceil(box.bottom * sy).toFloat()
        )
    }.toFloatArray()
    return ScoringImage(inputs.normalized, inputs.normalizedShape, objects)
}

private fun tensor(environment: OrtEnvironment, values: FloatArray, shape: LongArray): OnnxTensor =
    OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), shape)

private fun checkCurrent(isCurrent: () -> Boolean) {
    if (!isCurrent()) throw CancellationException("分析已取消")
}

private const val HEIGHT_DIM = 2
private const val WIDTH_DIM = 3
private const val ROI_FIELDS = 5L
