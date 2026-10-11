package com.example.okulo.camera

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast

internal fun openCapturedPhoto(context: Context, uri: Uri?) {
    if (uri == null) return
    val actions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        listOf(MediaStore.ACTION_REVIEW, Intent.ACTION_VIEW)
    } else {
        listOf(Intent.ACTION_VIEW)
    }
    for (action in actions) {
        try {
            context.startActivity(
                Intent(action).setDataAndType(uri, "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
            return
        } catch (_: ActivityNotFoundException) {
            // Older gallery apps may only support viewing a single photo.
        }
    }
    Toast.makeText(context, "未找到可查看照片的应用。", Toast.LENGTH_SHORT).show()
}
