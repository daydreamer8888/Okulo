package com.example.okulo.camera

import android.content.Context
import com.example.okulo.composition.createCompositionAnalyzer
import java.io.Closeable

internal class CameraSession(context: Context) : Closeable {
    val capture = CameraCapture(context)
    val composition = CameraComposition(createCompositionAnalyzer(context))

    override fun close() {
        composition.close()
        capture.close()
    }
}
