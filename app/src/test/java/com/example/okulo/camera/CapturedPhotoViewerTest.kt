package com.example.okulo.camera

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29])
class CapturedPhotoViewerTest {
    private val photo = Uri.parse("content://media/external/images/media/42")

    @Test
    fun opensCapturedPhotoWithGalleryBrowsingWhenSupported() {
        val context = ViewerContext()
        openCapturedPhoto(context, photo)
        assertEquals(1, context.attempts.size)
        val intent = context.attempts.single()
        assertEquals(
            if (Build.VERSION.SDK_INT >= 29) MediaStore.ACTION_REVIEW else Intent.ACTION_VIEW,
            intent.action
        )
        assertEquals(photo, intent.data)
        assertEquals("image/*", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun galleryWithoutReviewSupportStillOpensTheCapturedPhoto() {
        val context = ViewerContext(setOf("android.provider.action.REVIEW"))
        openCapturedPhoto(context, photo)
        assertEquals(
            if (Build.VERSION.SDK_INT >= 29) {
                listOf("android.provider.action.REVIEW", Intent.ACTION_VIEW)
            } else {
                listOf(Intent.ACTION_VIEW)
            },
            context.attempts.map { it.action }
        )
        val fallback = context.attempts.last()
        assertEquals(photo, fallback.data)
        assertEquals("image/*", fallback.type)
        assertTrue(fallback.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun missingViewersExplainTheFailureAfterTryingAvailableActions() {
        val context = ViewerContext(setOf("android.provider.action.REVIEW", Intent.ACTION_VIEW))
        openCapturedPhoto(context, photo)
        assertEquals("未找到可查看照片的应用。", ShadowToast.getTextOfLatestToast())
        assertEquals(if (Build.VERSION.SDK_INT >= 29) 2 else 1, context.attempts.size)
    }

    private class ViewerContext(private val unavailable: Set<String> = emptySet()) :
        ContextWrapper(RuntimeEnvironment.getApplication()) {
        val attempts = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            attempts += Intent(intent)
            if (intent.action in unavailable) throw ActivityNotFoundException()
        }
    }
}
