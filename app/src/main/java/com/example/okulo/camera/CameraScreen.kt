package com.example.okulo.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun CameraScreen(
    capture: CameraCapture,
    onImportPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasPermission()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) capture.takePhoto() else capture.permissionDenied()
    }
    CameraLayout(
        onImportPhoto = onImportPhoto,
        onSettings = onSettings,
        modifier = modifier,
        capture = capture.state,
        onMessageDismissed = capture::dismissMessage,
        onCapture = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                capture.takePhoto()
            }
        },
        onViewPhoto = {
            capture.state.savedPhoto?.let { uri ->
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "未找到可查看照片的应用。", Toast.LENGTH_SHORT).show()
                }
            }
        }
    ) {
        if (granted) {
            Box(Modifier.fillMaxSize().semantics { contentDescription = "取景区域" }) {
                CameraPreview(capture)
            }
        } else {
            CameraPermissionNotice(denied) { request.launch(Manifest.permission.CAMERA) }
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
