package com.ember.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.glance.appwidget.updateAll
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import com.ember.app.data.remote.dto.FeedItem
import java.io.File
import java.io.FileOutputStream

private const val TAG = "WidgetPhotoSync"

/** Keeps the home-screen widget's cached photo in step with whatever the app itself just
 * fetched — called after [com.ember.app.ui.home.HomeViewModel] loads the feed. Never hits the
 * backend on its own: it only asks Coil for the bitmap behind a URL the app's own UI already
 * requested, which Coil serves from its memory/disk cache in the common case since the Home
 * screen just rendered that exact photo. */
object WidgetPhotoSync {

    suspend fun sync(context: Context, feedItems: List<FeedItem>) {
        val latest = feedItems
            .mapNotNull { item -> item.photos.maxByOrNull { it.createdAt }?.let { item.displayName to it } }
            .maxByOrNull { (_, photo) -> photo.createdAt }
            ?: return

        val (senderName, photo) = latest
        applyLatestPhoto(context, senderName = senderName, photoId = photo.photoId, createdAtIso = photo.createdAt, photoUrl = photo.photoUrl)
    }

    /** Called directly from EmberFirebaseMessagingService's data-only NEW_PHOTO push — unlike
     * [sync], this never touches the network for anything but the image bytes themselves: the
     * push payload already carries everything else needed (sender name, photo id, timestamp,
     * URL), so there's no feed refetch involved in updating the widget at all. */
    suspend fun syncFromPush(context: Context, photoId: String, photoUrl: String, senderName: String, createdAtIso: String) {
        applyLatestPhoto(context, senderName = senderName, photoId = photoId, createdAtIso = createdAtIso, photoUrl = photoUrl)
    }

    private suspend fun applyLatestPhoto(context: Context, senderName: String, photoId: String, createdAtIso: String, photoUrl: String) {
        val store = WidgetPhotoStore(context)
        val current = store.current()
        // Guards against both an exact repeat (FCM's at-least-once delivery can redeliver the
        // same push) and an out-of-order arrival (two pushes landing out of sequence shouldn't
        // let the older one clobber a newer one already applied) — ISO-8601 UTC timestamps
        // compare correctly as plain strings.
        val isNewer = current == null || (photoId != current.photoId && createdAtIso >= current.createdAtIso)
        if (isNewer) {
            val bitmap = fetchBitmap(context, photoUrl)
            if (bitmap != null) {
                val file = File(context.filesDir, "widget_photo.jpg")
                val saved = runCatching {
                    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                }.onFailure { Log.w(TAG, "Failed to cache widget photo", it) }.isSuccess

                if (saved) {
                    store.save(
                        WidgetPhotoState(
                            photoId = photoId,
                            senderName = senderName,
                            createdAtIso = createdAtIso,
                            localFilePath = file.absolutePath,
                        ),
                    )
                }
            }
        }
        // Always refresh the placed widget, even when the cached photo itself didn't change —
        // cheap and local, and guarantees a stale/blank render self-heals next time the app opens.
        EmberWidget().updateAll(context)
    }

    private suspend fun fetchBitmap(context: Context, url: String): Bitmap? {
        // A different (smaller) request size than whatever the Home screen asked for won't hit
        // Coil's in-memory bitmap cache, but it still resolves from Coil's on-disk cache — the
        // Home screen's request already pulled these bytes down moments earlier — so this still
        // never re-hits the network on its own. Bounding the size keeps the widget's cached JPEG
        // and its decoded RemoteViews bitmap small (a full-res photo can be tens of MB decoded).
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size(512, 512))
            .allowHardware(false) // need a software bitmap so it can be JPEG-compressed to disk
            .build()
        val result = context.imageLoader.execute(request)
        if (result !is SuccessResult) {
            Log.w(TAG, "Couldn't fetch widget photo: $url")
            return null
        }
        return (result.image as? BitmapImage)?.bitmap
    }
}
