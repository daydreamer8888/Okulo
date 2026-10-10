package com.example.okulo.camera

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.okulo.composition.CropBox
import com.example.okulo.photo.CropExporter
import com.example.okulo.photo.PhotoTransform
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors

internal class CameraCapture(
    private val context: Context,
    private val shoot: ((ImageCapture.OutputFileOptions, ImageCapture.OnImageSavedCallback) -> Unit)? = null
) : Closeable {
    private val exports = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    val controller by lazy {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            isPinchToZoomEnabled = false
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }
    var state by mutableStateOf(CaptureUiState())
        private set

    fun previewReady(ready: Boolean) {
        state = state.copy(ready = ready)
    }

    fun reportMessage(message: String) {
        state = state.copy(message = message)
    }

    fun dismissMessage() {
        state = state.copy(message = null)
    }

    fun takePhoto(crop: CropBox? = null, keepOriginal: Boolean = true) {
        if (!state.ready || state.saving) return
        state = state.copy(saving = true, message = null)
        var output: CaptureOutput? = null
        try {
            val destination = captureOutput(context, cropOnly = crop != null && !keepOriginal)
            output = destination
            capture(destination.options, captureCallback(destination, crop))
        } catch (exception: IOException) {
            failed(exception, output)
        } catch (exception: SecurityException) {
            failed(exception, output)
        } catch (exception: IllegalStateException) {
            failed(exception, output)
        }
    }

    private fun captureCallback(output: CaptureOutput, crop: CropBox?) = object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
            val uri = output.source(result)
            if (uri != null && crop != null) {
                exportCrop(uri, crop, output)
            } else {
                complete(uri)
                output.close()
            }
        }

        override fun onError(exception: ImageCaptureException) = failed(exception, output)
    }

    private fun capture(output: ImageCapture.OutputFileOptions, callback: ImageCapture.OnImageSavedCallback) {
        val adapter = shoot
        if (adapter != null) {
            adapter(output, callback)
        } else {
            controller.takePicture(output, ContextCompat.getMainExecutor(context), callback)
        }
    }

    @Suppress("TooGenericExceptionCaught") // Keep a successful original when secondary crop export fails.
    private fun exportCrop(original: Uri, crop: CropBox, output: CaptureOutput) {
        exports.execute {
            try {
                val cropped = CropExporter(context).save(original, PhotoTransform(), crop)
                main.post { complete(cropped) }
            } catch (failure: Exception) {
                Log.e("OkuloCamera", "Capture crop export failed", failure)
                main.post {
                    if (output.keepsOriginal) {
                        state = state.copy(saving = false, savedPhoto = original, message = "裁剪保存失败，原图已保留")
                    } else {
                        failed(failure)
                    }
                }
            } finally {
                output.close()
            }
        }
    }

    private fun complete(uri: Uri?) {
        state = state.copy(
            saving = false,
            savedPhoto = uri ?: state.savedPhoto,
            message = if (uri != null) "已保存到相册" else "保存失败，请重试。"
        )
    }

    override fun close() { exports.shutdown() }

    private fun failed(exception: Exception, output: CaptureOutput? = null) {
        output?.close()
        Log.e("OkuloCamera", "Photo capture failed", exception)
        state = state.copy(saving = false, message = "保存失败，请重试。")
    }
}
