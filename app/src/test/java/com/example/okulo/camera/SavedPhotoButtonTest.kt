package com.example.okulo.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedPhotoButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun missingPhotosKeepTheViewerVisibleButDisabled() {
        var views = 0
        compose.setContent { SavedPhotoButton(null) { views++ } }
        compose.onNodeWithContentDescription("查看最新照片").assertIsNotEnabled().performClick()
        assertEquals(0, views)
        compose.onNodeWithTag("saved-photo-thumbnail", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun returningAfterDeletionRemovesThePhotoAndDisablesItsViewer() {
        val file = File.createTempFile("saved-photo", ".jpg", compose.activity.cacheDir)
        val image = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.RED)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        image.recycle()
        var views = 0
        try {
            compose.setContent { SavedPhotoButton(Uri.fromFile(file)) { views++ } }
            val photo = compose.onNodeWithContentDescription("查看最新照片")
            compose.waitUntil(5_000) { !photo.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
            compose.onNodeWithTag("saved-photo-thumbnail", useUnmergedTree = true).assertExists()
            photo.assertIsEnabled().performClick()
            assertEquals(1, views)
            // Returning without deletion must preserve the photo.
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(5_000) { !photo.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
            photo.assertIsEnabled()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(file.delete())
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(5_000) { photo.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
            photo.assertIsNotEnabled().performClick()
            compose.onNodeWithTag("saved-photo-thumbnail", useUnmergedTree = true).assertDoesNotExist()
            assertEquals(1, views)
        } finally {
            file.delete()
        }
    }
}
