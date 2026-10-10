package com.example.okulo.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.photo.CropActions

@Composable
internal fun CameraScreen(
    capture: CameraCapture,
    composition: CameraComposition,
    onImportPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
    analysisMode: AnalysisMode = AnalysisMode.Fast,
    showScores: Boolean = false,
    keepOriginal: Boolean = true
) {
    val context = LocalContext.current
    val framing by composition.state.collectAsState()
    var frameSource by remember { mutableStateOf<(() -> Bitmap?)?>(null) }
    val frameActions = CameraFrameActions(composition, { frameSource?.invoke() }, { analysisMode })
    val permission = rememberCameraPermission()
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) capture.takePhoto(framing.crop, keepOriginal) else capture.permissionDenied()
    }
    CameraLayout(
        onImportPhoto = onImportPhoto,
        onSettings = onSettings,
        modifier = modifier,
        capture = capture.state,
        composition = framing,
        compositionActions = CameraCompositionActions(
            frameActions::recommend,
            composition::restore,
            composition::dismiss,
            composition::dismissError
        ),
        showScores = showScores,
        onMessageDismissed = capture::dismissMessage,
        onCapture = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                capture.takePhoto(framing.crop, keepOriginal)
            }
        },
        onViewPhoto = { openCapturedPhoto(context, capture.state.savedPhoto) }
    ) {
        if (permission.granted) {
            Box(Modifier.fillMaxSize().semantics { contentDescription = "取景区域" }) {
                CameraViewfinder(framing.crop, CropActions(composition::updateCrop, frameActions::score)) {
                    CameraPreview(
                        capture,
                        onFrameSource = { frameSource = it },
                        onScene = composition::observeScene
                    )
                }
            }
        } else {
            CameraPermissionNotice(permission.denied, permission.request)
        }
    }
}

@Composable
private fun CameraPermissionNotice(denied: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Okulo")
        Text(if (denied) "相机权限未开启，请在设置中允许使用相机。" else "开启相机权限以使用取景功能。")
        Button(onClick = {
            if (denied) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } else {
                onRequest()
            }
        }) {
            Text(if (denied) "打开权限设置" else "开启相机")
        }
    }
}

private fun openCapturedPhoto(context: android.content.Context, uri: Uri?) {
    if (uri == null) return
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "未找到可查看照片的应用。", Toast.LENGTH_SHORT).show()
    }
}
