package com.ember.app.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ember.app.data.local.emberDataStore
import kotlinx.coroutines.flow.first

/** What the home-screen widget shows: a snapshot of the single most recent photo across all
 * friends, plus the local file it was cached to. Written only as a side effect of the app's own
 * feed loads (see [WidgetPhotoSync]) — the widget itself never calls the backend. */
data class WidgetPhotoState(
    val photoId: String,
    val senderName: String,
    val createdAtIso: String,
    val localFilePath: String,
)

class WidgetPhotoStore(private val context: Context) {

    private val photoIdKey = stringPreferencesKey("widget_photo_id")
    private val senderNameKey = stringPreferencesKey("widget_sender_name")
    private val createdAtKey = stringPreferencesKey("widget_created_at")
    private val filePathKey = stringPreferencesKey("widget_file_path")

    suspend fun current(): WidgetPhotoState? {
        val prefs = context.emberDataStore.data.first()
        val photoId = prefs[photoIdKey] ?: return null
        val senderName = prefs[senderNameKey] ?: return null
        val createdAtIso = prefs[createdAtKey] ?: return null
        val filePath = prefs[filePathKey] ?: return null
        return WidgetPhotoState(photoId, senderName, createdAtIso, filePath)
    }

    suspend fun save(state: WidgetPhotoState) {
        context.emberDataStore.edit {
            it[photoIdKey] = state.photoId
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
            it.remove(senderNameKey)
            it.remove(createdAtKey)
            it.remove(filePathKey)
        }
        existing?.localFilePath?.let { java.io.File(it).delete() }
    }
}
