package com.example.okulo.camera

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast

internal fun openCapturedPhoto(context: Context, uri: Uri?) {
    if (uri == null) return
    val mediaType = try {
        context.contentResolver.getType(uri) ?: "image/*"
    } catch (_: SecurityException) {
        "image/*"
    } catch (_: IllegalArgumentException) {
        "image/*"
    }
    val actions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        listOf(MediaStore.ACTION_REVIEW, Intent.ACTION_VIEW)
    } else {
        listOf(Intent.ACTION_VIEW)
    }
    val gallery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
    val viewer = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mediaType)
    val packages = listOf(gallery, viewer).flatMap { intent ->
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY)
            .map { it.activityInfo.packageName }
    }.distinct()
    for (packageName in packages) {
        for (action in actions) {
            try {
                context.startActivity(
                    Intent(action).setPackage(packageName).setDataAndType(uri, mediaType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
                return
            } catch (_: ActivityNotFoundException) {
                // A system gallery may only support ACTION_VIEW.
            }
        }
    }
    Toast.makeText(context, "未找到系统相册。", Toast.LENGTH_SHORT).show()
}
