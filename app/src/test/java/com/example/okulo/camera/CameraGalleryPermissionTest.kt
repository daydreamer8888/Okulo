package com.example.okulo.camera

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Suppress("DEPRECATION") // Deliver platform permission results to the registered activity-result callback.
class CameraGalleryPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun prepareSystemGallery() { registerSystemGallery() }

    @Test
    fun firstGalleryTapRequestsPhotoAccessThenOpensTheLatestCameraPhotoWithItsMediaType() {
        val gallery = gallery().apply {
            visibleIds = emptySet()
            mimeType = "image/heic"
        }
        try {
            compose.setContent { CameraGalleryButton(null) }
            compose.onNodeWithContentDescription("查看最新照片").assertIsEnabled().performClick()
            assertNull(shadowOf(compose.activity).nextStartedActivity?.data)
            val request = checkNotNull(shadowOf(compose.activity).lastRequestedPermission)
            val expected = if (Build.VERSION.SDK_INT >= 34) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            assertArrayEquals(expected, request.requestedPermissions)
            gallery.visibleIds = null
            shadowOf(compose.activity.application).grantPermissions(expected.first())
            compose.runOnIdle {
                compose.activity.onRequestPermissionsResult(
                    request.requestCode,
                    expected,
                    expected.map { PackageManager.PERMISSION_GRANTED }.toIntArray()
                )
            }
            awaitGallery()
            val intent = shadowOf(compose.activity).nextStartedActivity
            assertEquals(gallery.uri(20), intent.data)
            assertEquals("image/heic", intent.type)
        } finally {
            compose.activityRule.scenario.close()
            gallery.close()
        }
    }

    @Test
    @Config(sdk = [34])
    fun decliningPhotoAccessStillOpensOwnPhotosWithoutRequestingAgain() {
        val gallery = gallery().apply { visibleIds = setOf(10) }
        try {
            compose.setContent { CameraGalleryButton(gallery.uri(10)) }
            val photo = compose.onNodeWithContentDescription("查看最新照片")
            photo.assertIsEnabled().performClick()
            assertNull(shadowOf(compose.activity).nextStartedActivity?.data)
            val request = checkNotNull(shadowOf(compose.activity).lastRequestedPermission)
            compose.runOnIdle {
                compose.activity.onRequestPermissionsResult(
                    request.requestCode,
                    request.requestedPermissions,
                    request.requestedPermissions.map { PackageManager.PERMISSION_DENIED }.toIntArray()
                )
            }
            awaitGallery()
            assertEquals(gallery.uri(10), shadowOf(compose.activity).nextStartedActivity.data)
            photo.performClick()
            awaitGallery()
            assertEquals(gallery.uri(10), shadowOf(compose.activity).nextStartedActivity.data)
        } finally {
            compose.activityRule.scenario.close()
            gallery.close()
        }
    }

    @Test
    @Config(sdk = [34])
    fun selectedPhotoAccessUsesOnlyAccessiblePhotosAndRefreshesWhenAccessChanges() {
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val gallery = gallery().apply { visibleIds = setOf(10) }
        try {
            compose.setContent { CameraGalleryButton(null) }
            val photo = compose.onNodeWithContentDescription("查看最新照片")
            compose.waitUntil(5_000) {
                !photo.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
            photo.performClick()
            awaitGallery()
            assertEquals(gallery.uri(10), shadowOf(compose.activity).nextStartedActivity.data)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            gallery.visibleIds = null
            shadowOf(compose.activity.application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            photo.performClick()
            awaitGallery()
            assertEquals(gallery.uri(20), shadowOf(compose.activity).nextStartedActivity.data)
        } finally {
            compose.activityRule.scenario.close()
            gallery.close()
        }
    }

    private fun gallery() = CameraAlbumProvider().also {
        it.attachInfo(compose.activity, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, it)
        it.add(10, "DCIM/Camera/", 100)
        it.add(20, "DCIM/Camera/", 200)
    }

    private fun awaitGallery() {
        compose.waitUntil(5_000) { shadowOf(compose.activity).peekNextStartedActivity() != null }
    }
}
