package com.example.okulo.camera

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CameraGalleryButton(savedPhoto: Uri?) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    DisposableEffect(resolver, lifecycle) {
        val media = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { revision++ }
        }
        val resume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, media)
        lifecycle.addObserver(resume)
        onDispose {
            resolver.unregisterContentObserver(media)
            lifecycle.removeObserver(resume)
        }
    }
    val photo by produceState(savedPhoto, savedPhoto, revision) {
        value = withContext(Dispatchers.IO) { latestCameraPhoto(resolver) ?: savedPhoto }
    }
    SavedPhotoButton(photo) {
        scope.launch {
            val latest = withContext(Dispatchers.IO) { latestCameraPhoto(resolver) ?: savedPhoto }
            openCapturedPhoto(context, latest)
        }
    }
}
