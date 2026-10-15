package com.example.okulo.camera

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.okulo.image.CropWriter
import com.example.okulo.photo.TestAnalyzer
import com.example.okulo.photo.WorkGate
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraFrozenWorkflowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun failedFrozenSaveKeepsTheEditAndSuccessfulRetryReturnsToLiveView() {
        val context = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("frozen-workflow-", ".jpg", context.cacheDir)
        val photo = FrozenCameraPhoto(Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888), file)
        val engine = TestAnalyzer()
        val composition = CameraComposition(engine)
        val gate = WorkGate()
        val writes = AtomicInteger()
        val exported = File(context.cacheDir, "frozen-workflow-saved.jpg")
        val saved = Uri.fromFile(exported)
        val writer = CropWriter { _, _, _ ->
            if (writes.incrementAndGet() == 1) {
                gate.block()
                throw IOException("Gallery unavailable")
            }
            exported.outputStream().use { photo.image.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            saved
        }
        CameraCapture(context, writer).use { capture ->
            try {
                composition.recommend(
                    checkNotNull(photo.image.copy(Bitmap.Config.ARGB_8888, false)),
                    frozenPhoto = photo
                )
                await { !composition.state.value.busy }
                compose.setContent {
                    OkuloTheme(darkTheme = true) {
                        CameraScreen(capture, composition, {}, keepOriginal = false, freezeComposition = true)
                    }
                }
                val crop = composition.state.value.crop
                val save = compose.onNodeWithContentDescription("保存")
                save.assertIsEnabled().performClick()
                gate.awaitEntry()
                save.assertIsNotEnabled().performClick()
                assertEquals(1, writes.get())
                gate.release()
                await { !capture.state.saving }
                compose.onNodeWithText("已定格").assertExists()
                assertEquals(crop, composition.state.value.crop)
                assertTrue(file.exists())
                save.assertIsEnabled().performClick()
                await { !capture.state.saving && capture.state.savedPhoto == saved }
                compose.onNodeWithText("已定格").assertDoesNotExist()
                compose.onNodeWithContentDescription("返回取景").assertDoesNotExist()
                compose.onNodeWithContentDescription("拍照").assertExists()
                awaitRecentPhoto()
                assertFalse(composition.state.value.active)
                assertEquals(2, writes.get())
                await { !file.exists() && photo.image.isRecycled }
            } finally {
                gate.release()
                exported.delete()
                composition.close()
                assertTrue(engine.closed.await(10, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun systemBackCancelsPendingCaptureBeforeLeavingTheCamera() {
        val context = RuntimeEnvironment.getApplication()
        val engine = TestAnalyzer()
        val composition = CameraComposition(engine)
        var captured: ((FrozenCameraPhoto?) -> Unit)? = null
        var exits = 0
        lateinit var back: OnBackPressedDispatcher
        CameraCapture(context).use { capture ->
            try {
                composition.recommendFrozen({ captured = it }, 0.2f)
                compose.setContent {
                    back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    BackHandler { exits++ }
                    OkuloTheme { CameraScreen(capture, composition, {}, freezeComposition = true) }
                }
                compose.onNodeWithContentDescription("取消推荐").assertExists()
                compose.runOnIdle { back.onBackPressed() }
                assertFalse(composition.state.value.active)
                assertEquals(0, exits)
                val file = File.createTempFile("late-frozen-", ".jpg", context.cacheDir)
                val late = FrozenCameraPhoto(Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888), file)
                compose.runOnIdle { checkNotNull(captured)(late) }
                assertTrue(late.image.isRecycled)
                assertFalse(file.exists())
                assertEquals(0, engine.analysisCalls.get())
                compose.runOnIdle { back.onBackPressed() }
                assertEquals(1, exits)
            } finally {
                composition.close()
                assertTrue(engine.closed.await(10, TimeUnit.SECONDS))
            }
        }
    }

    private fun awaitRecentPhoto() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("查看最新照片") and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
