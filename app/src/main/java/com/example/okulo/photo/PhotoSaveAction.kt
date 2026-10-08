package com.example.okulo.photo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
internal fun rememberPhotoSaveAction(onSave: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) onSave() else Toast.makeText(context, "保存照片需要存储权限", Toast.LENGTH_SHORT).show()
    }
    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            request.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            onSave()
        }
    }
}
