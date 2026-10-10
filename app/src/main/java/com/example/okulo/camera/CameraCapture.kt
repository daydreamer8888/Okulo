package com.example.okulo.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import java.io.File
import java.io.IOException
import java.util.UUID
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
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var state by mutableStateOf(CaptureUiState())
        private set

    fun previewReady(ready: Boolean) {
        state = state.copy(ready = ready)
    }

    fun permissionDenied() {
        state = state.copy(message = "保存权限未开启，请允许后重试。")
    }

    fun dismissMessage() {
        state = state.copy(message = null)
    }

    fun takePhoto(crop: CropBox? = null) {
        if (!state.ready || state.saving) return
        state = state.copy(saving = true, message = null)
        try {
            val output = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photoValues()
            ).build()
            capture(
                output,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        val uri = result.savedUri
                        if (uri != null && crop != null) exportCrop(uri, crop) else complete(uri)
                    }

                    override fun onError(exception: ImageCaptureException) = failed(exception)
                }
            )
        } catch (exception: IOException) {
            failed(exception)
        } catch (exception: SecurityException) {
            failed(exception)
        } catch (exception: IllegalStateException) {
            failed(exception)
        }
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
    private fun exportCrop(original: Uri, crop: CropBox) {
        exports.execute {
            try {
                val cropped = CropExporter(context).save(original, PhotoTransform(), crop)
                main.post { complete(cropped) }
            } catch (failure: Exception) {
                Log.e("OkuloCamera", "Capture crop export failed", failure)
                main.post {
                    state = state.copy(saving = false, savedPhoto = original, message = "裁剪保存失败，原图已保留")
                }
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

    private fun failed(exception: Exception) {
        Log.e("OkuloCamera", "Photo capture failed", exception)
        state = state.copy(saving = false, message = "保存失败，请重试。")
    }

    @Suppress("DEPRECATION")
    private fun photoValues(): ContentValues {
        val name = "Okulo_${UUID.randomUUID()}.jpg"
        return ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Okulo")
            } else {
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Okulo"
                )
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create photo directory")
                put(MediaStore.Images.Media.DATA, File(directory, name).absolutePath)
            }
        }
    }
}
