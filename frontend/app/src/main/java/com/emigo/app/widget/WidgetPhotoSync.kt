package com.emigo.app.widget

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
import com.emigo.app.data.remote.dto.FeedItem
import java.io.File
import java.io.FileOutputStream

private const val TAG = "WidgetPhotoSync"

/** Keeps the home-screen widget's cached photo in step with whatever the app itself just
 * fetched — called after [com.emigo.app.ui.home.HomeViewModel] loads the feed. Never hits the
 * backend on its own for the feed itself: it only asks Coil for the bitmap behind a URL the
 * app's own UI already requested, which Coil serves from its memory/disk cache in the common
 * case since the Home screen just rendered that exact photo.
 *
 * A Gold subscriber can choose a set of friends to always feature (see [WidgetPreferenceStore]);
 * everyone else (and a subscriber who hasn't chosen anyone) gets the original behavior — the
 * single most recent photo across every friend. Whether that choice is honored is decided
 * entirely from [WidgetPreferenceStore]'s locally-cached Gold status, not a live check here —
 * see that class's own doc comment for why. */
object WidgetPhotoSync {

    suspend fun sync(context: Context, feedItems: List<FeedItem>) {
        val effectiveFriendIds = effectiveFeaturedFriendIds(context)
        val candidates = if (effectiveFriendIds.isEmpty()) feedItems else feedItems.filter { it.friendId in effectiveFriendIds }

        val latest = candidates
            .mapNotNull { item -> item.photos.maxByOrNull { it.createdAt }?.let { photo -> Triple(item.friendId, item.displayName, photo) } }
            .maxByOrNull { (_, _, photo) -> photo.createdAt }
            ?: return

        val (friendId, senderName, photo) = latest
        applyLatestPhoto(
            context,
            friendId = friendId,
            senderName = senderName,
            photoId = photo.photoId,
            createdAtIso = photo.createdAt,
            photoUrl = photo.photoUrl,
            effectiveFriendIds = effectiveFriendIds,
        )
    }

    /** Called directly from EmberFirebaseMessagingService's data-only NEW_PHOTO push — unlike
     * [sync], this never touches the network for anything but the image bytes themselves: the
     * push payload already carries everything else needed, so there's no feed refetch involved
     * in updating the widget at all. [senderId] lets this honor the same featured-friend choice
     * [sync] does — a push from someone who isn't currently featured is silently ignored rather
     * than overriding the widget, exactly as if their photo just hadn't been the most recent one
     * during a normal sync. */
    suspend fun syncFromPush(context: Context, photoId: String, photoUrl: String, senderId: String, senderName: String, createdAtIso: String) {
        val effectiveFriendIds = effectiveFeaturedFriendIds(context)
        if (effectiveFriendIds.isNotEmpty() && senderId !in effectiveFriendIds) return

        applyLatestPhoto(
            context,
            friendId = senderId,
            senderName = senderName,
            photoId = photoId,
            createdAtIso = createdAtIso,
            photoUrl = photoUrl,
            effectiveFriendIds = effectiveFriendIds,
        )
    }

    /** The featured-friend set only actually applies while genuinely subscribed — a lapsed
     * subscription (cachedIsGoldMember false, refreshed once per app open rather than here) falls
     * back to the same empty-set "anyone" behavior a free account always had. */
    private suspend fun effectiveFeaturedFriendIds(context: Context): Set<String> {
        val store = WidgetPreferenceStore(context)
        val isGoldMember = store.cachedIsGoldMember()
        return if (isGoldMember) store.currentFeaturedFriendIds() else emptySet()
    }

    private suspend fun applyLatestPhoto(
        context: Context,
        friendId: String,
        senderName: String,
        photoId: String,
        createdAtIso: String,
        photoUrl: String,
        effectiveFriendIds: Set<String>,
    ) {
        val store = WidgetPhotoStore(context)
        val current = store.current()
        // Whatever's currently cached might no longer qualify at all — the user could have just
        // narrowed their featured-friend selection to exclude whoever that photo was from. A
        // disqualified photo must be replaced regardless of timestamp; only when the cached photo
        // still qualifies does the normal "is this candidate actually newer" comparison apply
        // (guards against both an exact repeat — FCM's at-least-once delivery can redeliver the
        // same push — and an out-of-order arrival).
        val currentStillQualifies = current != null && (effectiveFriendIds.isEmpty() || current.friendId in effectiveFriendIds)
        val isNewer = !currentStillQualifies || (photoId != current!!.photoId && createdAtIso >= current.createdAtIso)
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
                            friendId = friendId,
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
