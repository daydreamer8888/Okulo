package com.example.okulo.photo

import android.net.Uri
import android.util.Log
import com.example.okulo.composition.CropBox
import com.example.okulo.image.CropWriter
import com.example.okulo.image.PhotoTransform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns one save at a time. Arguments capture the edit at the moment of the request. */
internal class PhotoSaveController(private val scope: CoroutineScope, private val writer: CropWriter) {
    private val mutableSaving = MutableStateFlow(false)
    val saving = mutableSaving.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    @Suppress("TooGenericExceptionCaught") // Storage errors become actionable save feedback.
    fun save(source: Uri, transform: PhotoTransform, crop: CropBox) {
        if (mutableSaving.value) return
        mutableSaving.value = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { writer.save(source, transform, crop) }
                messages.send("已保存到相册")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e("OkuloExport", "Crop save failed", failure)
                messages.send("保存失败，请重试")
            } finally {
                mutableSaving.value = false
            }
        }
    }
}
