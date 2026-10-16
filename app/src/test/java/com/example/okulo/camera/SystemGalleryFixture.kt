package com.example.okulo.camera

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

internal fun registerSystemGallery() {
    registerPhotoViewer(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
        "example.gallery",
        system = true
    )
}

internal fun registerPhotoViewer(intent: Intent, packageName: String, system: Boolean) {
    val application = ApplicationInfo().apply {
        this.packageName = packageName
        flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
    }
    val activity = ActivityInfo().apply {
        this.packageName = packageName
        name = "$packageName.Viewer"
        applicationInfo = application
    }
    val info = ResolveInfo().apply { activityInfo = activity }
    shadowOf(RuntimeEnvironment.getApplication().packageManager).addResolveInfoForIntent(intent, info)
}
