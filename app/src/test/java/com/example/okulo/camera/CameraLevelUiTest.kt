package com.example.okulo.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraLevelUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val enabled = mutableStateOf(false)
    private val manager get() = compose.activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Test
    fun levelRespondsToGravityWithoutCoveringTouchOrChangingPreviewSize() {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_GRAVITY)
        shadowOf(manager).addSensor(sensor)
        var focusTaps = 0
        compose.setContent {
            OkuloTheme(darkTheme = true) {
                Box(Modifier.size(300.dp, 400.dp).testTag("viewfinder").clickable { focusTaps++ }) {
                    CameraLevel(enabled.value)
                }
            }
        }
        val viewfinder = compose.onNodeWithTag("viewfinder")
        val bounds = viewfinder.fetchSemanticsNode().boundsInRoot
        val level = compose.onNodeWithContentDescription("水平仪", useUnmergedTree = true)
        send(sensor, -1.7f, 9.65f)
        level.assertDoesNotExist()
        compose.runOnIdle { enabled.value = true }
        send(sensor, -1.7f, 9.65f)
        assertEquals("向右倾斜", level.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        val indicator = level.fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.center.x, indicator.center.x, 1f)
        assertEquals(bounds.center.y, indicator.center.y, 1f)
        viewfinder.performTouchInput { click(center) }
        assertEquals(1, focusTaps)
        send(sensor, 0f, 9.8f)
        assertEquals("已水平", level.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        send(sensor, 0f, 0f, 9.8f)
        level.assertDoesNotExist()
        assertEquals(bounds, viewfinder.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { enabled.value = false }
        compose.runOnIdle { assertTrue(shadowOf(manager).listeners.isEmpty()) }
    }

    @Test
    fun leavingTheCameraStopsSensorsAndResumingWaitsForAFreshReading() {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_GRAVITY)
        shadowOf(manager).addSensor(sensor)
        enabled.value = true
        compose.setContent { CameraLevel(enabled.value) }
        send(sensor, 0f, 9.8f)
        val level = compose.onNodeWithContentDescription("水平仪")
        level.assertExists()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(shadowOf(manager).listeners.isEmpty())
        send(sensor, 1.7f, 9.65f)
        level.assertDoesNotExist()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        level.assertDoesNotExist()
        send(sensor, 1.7f, 9.65f)
        assertEquals("向左倾斜", level.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    @Test
    fun accelerometerFallbackAccountsForTheDisplaysNaturalOrientation() {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowOf(manager).addSensor(sensor)
        shadowOf(compose.activity.windowManager.defaultDisplay).setRotation(Surface.ROTATION_90)
        enabled.value = true
        compose.setContent { CameraLevel(enabled.value) }
        send(sensor, 9.8f, 0f)
        assertEquals(
            "已水平",
            compose.onNodeWithContentDescription("水平仪").fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription]
        )
    }

    private fun send(sensor: Sensor, x: Float, y: Float, z: Float = 0f) {
        compose.runOnIdle {
            shadowOf(manager).sendSensorEventToListeners(
                SensorEventBuilder.newBuilder(sensor, floatArrayOf(x, y, z)).build(),
                sensor
            )
        }
    }
}
