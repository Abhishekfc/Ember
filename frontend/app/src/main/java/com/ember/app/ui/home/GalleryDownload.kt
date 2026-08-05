package com.ember.app.ui.home

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/** Downloads a Memories photo straight from its (already-public, R2) URL to the device's own
 * gallery — no re-encoding, the exact bytes already stored are what gets saved. Scoped-storage
 * (MediaStore.insert) on Android 10+ needs no permission at all; Android 9 and below still needs
 * WRITE_EXTERNAL_STORAGE (declared in the manifest, capped at that OS range), which the caller is
 * responsible for having already granted before calling this — see MemoriesScreen's own call
 * site for the runtime request on that older range. */
suspend fun saveImageToGallery(context: Context, photoUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = URL(photoUrl).openStream().use { it.readBytes() }
        val resolver = context.contentResolver
        val fileName = "Emigo_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Emigo")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val itemUri = resolver.insert(collection, values) ?: error("Couldn't create a gallery entry")
        resolver.openOutputStream(itemUri)?.use { it.write(bytes) } ?: error("Couldn't write image data")
        // IS_PENDING=1 above hides the file from other apps/the gallery while bytes are still
        // being written — clearing it is what actually makes it show up once done.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(itemUri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    }
}
