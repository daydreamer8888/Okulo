package com.example.okulo.camera

import android.Manifest
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.rule.GrantPermissionRule
import com.example.okulo.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CameraPreviewTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun previewShowsCameraPixelsAndStopsWhenLeavingTheShootingPage() {
        onView(withContentDescription("后置主摄实时预览")).check(matches(isDisplayed()))
        lateinit var preview: PreviewView
        onView(withContentDescription("后置主摄实时预览")).check { view: View, error ->
            if (error != null) throw error
            preview = view as PreviewView
        }
        compose.waitUntil(60_000) {
            compose.runOnIdle { preview.previewStreamState.value == PreviewView.StreamState.STREAMING }
        }
        compose.runOnIdle {
            assertEquals(CameraSelector.LENS_FACING_BACK, preview.controller?.cameraInfo?.lensFacing)
            val frame = checkNotNull(preview.bitmap)
            assertTrue(frame.width > 0 && frame.height > 0)
            val pixels = IntArray(frame.width * frame.height)
            frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
            assertTrue("Camera preview must contain an image", pixels.any { it != pixels[0] })
            frame.recycle()
        }
        compose.onNodeWithContentDescription("分析照片").performClick()
        compose.waitUntil(10_000) {
            compose.runOnIdle { preview.previewStreamState.value == PreviewView.StreamState.IDLE }
        }
        compose.onNodeWithText("返回拍摄").performClick()
        compose.onNodeWithText("开启相机").assertDoesNotExist()
        onView(withContentDescription("后置主摄实时预览")).check { view, error ->
            if (error != null) throw error
            preview = view as PreviewView
        }
        compose.waitUntil(10_000) {
            compose.runOnIdle { preview.previewStreamState.value == PreviewView.StreamState.STREAMING }
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(10_000) {
            preview.previewStreamState.value == PreviewView.StreamState.IDLE
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) {
            compose.runOnIdle { preview.previewStreamState.value == PreviewView.StreamState.STREAMING }
        }
    }
}
