package com.example.okulo.camera

import android.Manifest
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.example.okulo.photo.TestAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraGalleryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cameraAlbumRefreshesAcrossLaunchDeletionAndReturningFromAnotherCamera() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val gallery = CameraAlbumProvider()
        gallery.attachInfo(compose.activity, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, gallery)
        gallery.add(10, "DCIM/Camera/", 100)
        gallery.add(20, "DCIM/Camera/", 200)
        gallery.add(30, "Pictures/Screenshots/", 900)
        gallery.add(40, "Pictures/Okulo/", 800)
        if (android.os.Build.VERSION.SDK_INT >= 29) gallery.add(50, "DCIM/Camera/", 700, pending = true)
        val composition = CameraComposition(TestAnalyzer())
        CameraCapture(compose.activity).use { capture ->
            try {
                compose.setContent { CameraScreen(capture, composition, {}) }
                openLatestPhoto()
                assertEquals(gallery.uri(20), shadowOf(compose.activity).nextStartedActivity.data)
                gallery.remove(20)
                compose.waitForIdle()
                openLatestPhoto()
                assertEquals(gallery.uri(10), shadowOf(compose.activity).nextStartedActivity.data)
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                gallery.add(60, "DCIM/Camera/", 300)
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                openLatestPhoto()
                assertEquals(gallery.uri(60), shadowOf(compose.activity).nextStartedActivity.data)
            } finally {
                compose.activityRule.scenario.close()
                composition.close()
                gallery.close()
            }
        }
    }

    private fun openLatestPhoto() {
        val photo = compose.onNodeWithContentDescription("查看最新照片")
        compose.waitUntil(5_000) { !photo.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
        photo.performClick()
        compose.waitUntil(5_000) { shadowOf(compose.activity).peekNextStartedActivity() != null }
    }
}

internal class CameraAlbumProvider : ContentProvider(), AutoCloseable {
    private val database = SQLiteDatabase.create(null)
    private val files = mutableMapOf<Long, File>()
    var visibleIds: Set<Long>? = null
    var mimeType = "image/jpeg"

    override fun onCreate(): Boolean {
        database.execSQL(
            "CREATE TABLE images (_id INTEGER PRIMARY KEY, relative_path TEXT, _data TEXT, " +
                "datetaken INTEGER, date_added INTEGER, is_pending INTEGER, is_trashed INTEGER)"
        )
        return true
    }

    fun uri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    fun add(id: Long, folder: String, taken: Long, pending: Boolean = false) {
        val file = File(checkNotNull(context).cacheDir, "gallery-fixture-$id.jpg")
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        files[id] = file
        database.insertOrThrow(
            "images",
            null,
            ContentValues().apply {
                put("_id", id)
                put("relative_path", folder)
                put("_data", "/storage/emulated/0/${folder}fixture-$id.jpg")
                put("datetaken", taken)
                put("date_added", taken)
                put("is_pending", if (pending) 1 else 0)
                put("is_trashed", 0)
            }
        )
        checkNotNull(context).contentResolver.notifyChange(uri(id), null)
    }

    fun remove(id: Long) {
        database.delete("images", "_id = ?", arrayOf(id.toString()))
        files.remove(id)?.delete()
        checkNotNull(context).contentResolver.notifyChange(uri(id), null)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        args: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val isItem = uri.lastPathSegment?.toLongOrNull() != null
        val access = visibleIds?.let { if (it.isEmpty()) "0" else "_id IN (${it.joinToString(",")})" }
        val conditions = listOfNotNull(if (isItem) "_id = ?" else selection, access)
        return database.query(
            "images",
            projection,
            conditions.joinToString(" AND ").ifEmpty { null },
            if (isItem) arrayOf(uri.lastPathSegment) else args,
            null,
            null,
            sortOrder
        )
    }

    override fun getType(uri: Uri) = mimeType
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = ContentUris.parseId(uri)
        if (visibleIds?.contains(id) == false) throw FileNotFoundException()
        return ParcelFileDescriptor.open(
            files[id] ?: throw FileNotFoundException(),
            ParcelFileDescriptor.MODE_READ_ONLY
        )
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only fixture")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int =
        error("Read-only fixture")
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("Read-only fixture")
    override fun close() {
        database.close()
        files.values.forEach { it.delete() }
    }
}
