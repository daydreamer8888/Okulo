package com.example.okulo.camera

import android.graphics.Bitmap
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Keep the captured source alive while analysis or export still uses it. */
internal class FrozenCameraPhoto(val image: Bitmap, private val file: File) : Closeable {
    val uri: Uri get() = Uri.fromFile(file)
    private val owners = AtomicInteger(1)

    fun retain(): FrozenCameraPhoto {
        owners.updateAndGet { count ->
            check(count > 0)
            count + 1
        }
        return this
    }

    override fun close() {
        if (owners.decrementAndGet() == 0) {
            image.recycle()
            file.delete()
        }
    }
}
