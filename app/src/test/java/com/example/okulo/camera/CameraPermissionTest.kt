package com.example.okulo.camera

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.example.okulo.MainActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun deniedPermissionOffersAnAppSettingsRecoveryAction() {
        compose.onNodeWithText("开启相机").performClick()
        val request = shadowOf(compose.activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.CAMERA), request.requestedPermissions)
        compose.runOnIdle {
            compose.activity.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED)
            )
        }
        compose.onNodeWithText("相机权限未开启，请在设置中允许使用相机。").assertExists()
        compose.onNodeWithText("打开权限设置").performClick()
        val settings = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.action)
        assertEquals("package:${compose.activity.packageName}", settings.data.toString())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.CAMERA)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithContentDescription("取景区域").assertExists()
        compose.onNodeWithText("打开权限设置").assertDoesNotExist()
    }
}
