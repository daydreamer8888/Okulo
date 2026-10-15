package com.example.okulo.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun CameraSessionLifetime(session: CameraSession, keepComposition: () -> Boolean = { false }) {
    val keep by rememberUpdatedState(keepComposition)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !keep()) session.composition.invalidateScene()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            session.close()
        }
    }
}
