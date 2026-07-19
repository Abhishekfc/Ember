package com.ember.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** Persists, per friend, the photoId of the newest photo the user has actually opened — so the
 * Home avatar ring stays gray across app restarts instead of resetting to "unseen" (gradient)
 * every time a fresh HomeViewModel is created. */
class SeenPhotoStore(private val context: Context) {

    private val key = stringPreferencesKey("seen_photo_by_friend")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun current(): Map<String, String> {
        val raw = context.emberDataStore.data.first()[key] ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    suspend fun save(seenByFriend: Map<String, String>) {
        context.emberDataStore.edit { it[key] = json.encodeToString(seenByFriend) }
    }
}
