package com.example.okulo.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.okulo.photo.recommendation
import com.example.okulo.ui.crop.CropActions
import com.example.okulo.ui.theme.OkuloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraFrozenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun frozenEditingShowsSaveProgressAndAllowsRetryWithoutChangingTheViewfinder() {
        val photo = FrozenCameraPhoto(
            Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888),
            File.createTempFile("frozen-save-ui-", ".jpg", RuntimeEnvironment.getApplication().cacheDir)
        )
        val result = recommendation()
        val state = mutableStateOf(CameraCompositionState(result, result.crop, frozenPhoto = photo))
        val capture = mutableStateOf(CaptureUiState(ready = true))
        try {
            compose.setContent {
                OkuloTheme(darkTheme = true) {
                    CameraLayout(
                        {},
                        capture = capture.value,
                        composition = state.value,
                        onCapture = { capture.value = capture.value.copy(saving = true) },
                        compositionActions = CameraCompositionActions(
                            dismiss = { state.value = CameraCompositionState() }
                        )
                    ) {
                        CameraViewfinder(state.value.crop, CropActions(), frozenPhoto = state.value.frozenPhoto) {
                            Text("实时取景")
                        }
                    }
                }
            }
            compose.onNodeWithText("已定格").assertExists()
            compose.onNodeWithContentDescription("拍照").assertDoesNotExist()
            val save = compose.onNodeWithContentDescription("保存")
            val shutter = save.fetchSemanticsNode().boundsInRoot
            val image = compose.onNodeWithContentDescription("定格画面").fetchSemanticsNode().boundsInRoot
            save.assertIsEnabled().performClick().assertIsNotEnabled()
            val progress = compose.onNodeWithContentDescription("正在保存", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(progress.left >= shutter.left && progress.right <= shutter.right)
            compose.onNodeWithContentDescription("返回取景").assertIsNotEnabled()
            compose.runOnIdle { capture.value = capture.value.copy(saving = false, message = "保存失败，请重试。") }
            save.assertIsEnabled()
            assertEquals(image, compose.onNodeWithContentDescription("定格画面").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithContentDescription("返回取景").performClick()
            compose.onNodeWithText("已定格").assertDoesNotExist()
            compose.onNodeWithContentDescription("定格画面").assertDoesNotExist()
            assertEquals(shutter, compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot)
        } finally {
            photo.close()
        }
    }

    @Test
    fun theStillCoversTheLivePreviewAndClosingItRestoresTheSameViewfinderArea() {
        val bitmap = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val photo =
            FrozenCameraPhoto(
                bitmap,
                File.createTempFile("frozen-ui-", ".jpg", RuntimeEnvironment.getApplication().cacheDir)
            )
        val frozen = mutableStateOf<FrozenCameraPhoto?>(photo)
        lateinit var view: View
        try {
            compose.setContent {
                view = LocalView.current
                OkuloTheme {
                    Box(Modifier.size(240.dp, 320.dp)) {
                        CameraViewfinder(null, CropActions(), frozenPhoto = frozen.value) { Text("实时取景") }
                    }
                }
            }
            val image = compose.onNodeWithContentDescription("定格画面")
            val bounds = image.fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                val rendered = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(rendered))
                assertTrue(Color.red(rendered.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())) > 230)
                rendered.recycle()
            }
            assertEquals(240.dp.value * compose.density.density, bounds.width, 1f)
            assertEquals(320.dp.value * compose.density.density, bounds.height, 1f)
            compose.runOnIdle { frozen.value = null }
            image.assertDoesNotExist()
            compose.onNodeWithText("实时取景").assertExists()
        } finally {
            photo.close()
        }
    }
}
