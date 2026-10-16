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
import com.example.okulo.image.CropExporter
import com.example.okulo.image.CropWriter
import com.example.okulo.image.GalleryAlbum
import com.example.okulo.image.PhotoTransform
import com.example.okulo.image.readPhoto
import com.example.okulo.image.saveOriginalPhoto
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors

@Suppress("TooManyFunctions") // Capture and frozen export share the shutter state and camera lifetime.
internal class CameraCapture(
    private val context: Context,
    private val writer: CropWriter = CropExporter(context, GalleryAlbum.Camera),
    private val shoot: ((ImageCapture.OutputFileOptions, ImageCapture.OnImageSavedCallback) -> Unit)? = null
) : Closeable {
    private val exports = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
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
        if (!state.canCapture) return
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

    fun freeze(onFrame: (FrozenCameraPhoto?) -> Unit) {
        if (closed || !state.canCapture) {
            onFrame(null)
            return
        }
        state = state.copy(preparing = true, message = null)
        val output = runCatching { captureOutput(context, cropOnly = true) }.getOrElse {
            state = state.copy(preparing = false)
            onFrame(null)
            return
        }
        val callback = object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (closed) {
                    output.close()
                    return
                }
                exports.execute {
                    val photo = runCatching {
                        val uri = checkNotNull(output.source(result))
                        FrozenCameraPhoto(readPhoto(context.contentResolver, uri), java.io.File(checkNotNull(uri.path)))
                    }.getOrNull()
                    if (photo == null) output.close()
                    main.post {
                        state = state.copy(preparing = false)
                        if (closed) photo?.close() else onFrame(photo)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                output.close()
                state = state.copy(preparing = false)
                onFrame(null)
            }
        }
        runCatching { capture(output.options, callback) }.onFailure {
            output.close()
            state = state.copy(preparing = false)
            onFrame(null)
        }
    }

    @Suppress("TooGenericExceptionCaught") // Preserve an exported original if the crop export fails.
    fun saveFrozen(
        photo: FrozenCameraPhoto,
        crop: CropBox,
        keepOriginal: Boolean,
        onSaved: () -> Unit = {}
    ) {
        if (closed || state.saving || state.preparing) return
        val source = photo.retain()
        state = state.copy(saving = true, message = null)
        exports.execute {
            var original: Uri? = null
            try {
                if (keepOriginal) original = saveOriginalPhoto(context.contentResolver, source.uri)
                val cropped = writer.save(source.uri, PhotoTransform(), crop)
                main.post {
                    complete(cropped)
                    if (!closed) onSaved()
                }
            } catch (failure: Exception) {
                val retained = original
                main.post {
                    if (retained != null) {
                        state = state.copy(saving = false, savedPhoto = retained, message = "裁剪保存失败，原图已保留")
                    } else {
                        failed(failure)
                    }
                }
            } finally {
                source.close()
            }
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
                val cropped = writer.save(original, PhotoTransform(), crop)
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

    override fun close() {
        closed = true
        exports.shutdown()
    }

    private fun failed(exception: Exception, output: CaptureOutput? = null) {
        output?.close()
        Log.e("OkuloCamera", "Photo capture failed", exception)
        state = state.copy(saving = false, message = "保存失败，请重试。")
    }
}
