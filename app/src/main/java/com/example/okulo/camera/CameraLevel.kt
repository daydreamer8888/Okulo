@file:Suppress("MagicNumber") // Gravity filtering and the compact level's preview geometry.

package com.example.okulo.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.okulo.ui.theme.LevelAlignedDark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
internal fun CameraLevel(enabled: Boolean) {
    val roll = rememberLevelRoll(enabled) ?: return
    val aligned = abs(roll) <= .7f
    val angle by animateFloatAsState(if (aligned) 0f else roll.coerceIn(-20f, 20f), tween(120), label = "level")
    val foreground = if (aligned) LevelAlignedDark else Color.White
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.align(Alignment.Center).size(128.dp, 32.dp).semantics {
                contentDescription = "水平仪"
                stateDescription = if (aligned) "已水平" else if (roll > 0f) "向左倾斜" else "向右倾斜"
            }
        ) {
            fun line(start: Offset, end: Offset) {
                drawLine(Color.Black.copy(alpha = .7f), start, end, 4.dp.toPx(), StrokeCap.Round)
                drawLine(foreground, start, end, 2.dp.toPx(), StrokeCap.Round)
            }
            val y = center.y
            line(Offset(0f, y), Offset(20.dp.toPx(), y))
            line(Offset(size.width - 20.dp.toPx(), y), Offset(size.width, y))
            rotate(angle) { line(Offset(28.dp.toPx(), y), Offset(size.width - 28.dp.toPx(), y)) }
            drawCircle(Color.Black.copy(alpha = .7f), 4.dp.toPx())
            drawCircle(foreground, 3.dp.toPx())
            if (!aligned) drawCircle(Color.Black, 1.5.dp.toPx())
        }
    }
}

@Composable
private fun rememberLevelRoll(enabled: Boolean): Float? {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    var roll by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(enabled, manager, owner, view) {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var listening = false
        var gravity: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!listening || event.values.size < 3 || event.values.any { !it.isFinite() }) return
                val previous = gravity
                val values = if (sensor?.type == Sensor.TYPE_ACCELEROMETER && previous != null) {
                    FloatArray(3) { previous[it] * .8f + event.values[it] * .2f }
                } else {
                    event.values.copyOf()
                }
                gravity = values
                val (x, y) = when (view.display?.rotation) {
                    Surface.ROTATION_90 -> -values[1] to values[0]
                    Surface.ROTATION_180 -> -values[0] to -values[1]
                    Surface.ROTATION_270 -> values[1] to -values[0]
                    else -> values[0] to values[1]
                }
                roll = if (hypot(x, y) < 3f) {
                    null
                } else {
                    val degrees = Math.toDegrees(atan2(x, y).toDouble()).toFloat()
                    when {
                        degrees > 90f -> degrees - 180f
                        degrees < -90f -> degrees + 180f
                        else -> degrees
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun update() {
            val shouldListen = enabled && sensor != null &&
                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (shouldListen && !listening) {
                gravity = null
                listening = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            } else if (!shouldListen) {
                listening = false
                manager.unregisterListener(listener)
                roll = null
            }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            listening = false
            manager.unregisterListener(listener)
            owner.lifecycle.removeObserver(observer)
            roll = null
        }
    }
    return roll
}
