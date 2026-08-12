package com.emigo.app.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.emigo.app.data.local.emberDataStore
import kotlinx.coroutines.flow.first

/** What the home-screen widget shows: a snapshot of the most recent qualifying photo (see
 * [WidgetPhotoSync] for what "qualifying" means — everyone by default, or a Gold subscriber's
 * chosen set of friends), plus the local file it was cached to. [friendId] is what lets a later
 * sync tell whether this cached photo actually came from one of the currently-chosen friends.
 * Written only as a side effect of the app's own feed loads — the widget itself never calls the
 * backend. */
data class WidgetPhotoState(
    val photoId: String,
    val friendId: String,
    val senderName: String,
    val createdAtIso: String,
    val localFilePath: String,
)

class WidgetPhotoStore(private val context: Context) {

    private val photoIdKey = stringPreferencesKey("widget_photo_id")
    private val friendIdKey = stringPreferencesKey("widget_friend_id")
    private val senderNameKey = stringPreferencesKey("widget_sender_name")
    private val createdAtKey = stringPreferencesKey("widget_created_at")
    private val filePathKey = stringPreferencesKey("widget_file_path")

    suspend fun current(): WidgetPhotoState? {
        val prefs = context.emberDataStore.data.first()
        val photoId = prefs[photoIdKey] ?: return null
        val friendId = prefs[friendIdKey] ?: return null
        val senderName = prefs[senderNameKey] ?: return null
        val createdAtIso = prefs[createdAtKey] ?: return null
        val filePath = prefs[filePathKey] ?: return null
        return WidgetPhotoState(photoId, friendId, senderName, createdAtIso, filePath)
    }

    suspend fun save(state: WidgetPhotoState) {
        context.emberDataStore.edit {
            it[photoIdKey] = state.photoId
            it[friendIdKey] = state.friendId
            it[senderNameKey] = state.senderName
            it[createdAtKey] = state.createdAtIso
            it[filePathKey] = state.localFilePath
        }
    }

    /** Called on sign-out — without this, a friend's private photo (and their name) keeps
     * rendering on the home screen indefinitely after "signing out," since the widget reads
     * straight from this store independent of the app's own signed-in state. */
    suspend fun clear() {
        val existing = current()
        context.emberDataStore.edit {
            it.remove(photoIdKey)
            it.remove(friendIdKey)
            it.remove(senderNameKey)
            it.remove(createdAtKey)
            it.remove(filePathKey)
        }
        existing?.localFilePath?.let { java.io.File(it).delete() }
    }
}
