package com.ember.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** Instagram/Snapchat-style instant-on-reopen: the last successfully fetched first page of
 * feed/friends/activity/memories, so restarting the app shows that immediately instead of a
 * blank/spinner state while the network round trip is still in flight — the fresh fetch still
 * always happens right away underneath, it just no longer has to be *waited on* before there's
 * something to show. Not a general offline-sync layer, and not paginated itself: only the one
 * first page each screen reads on its very first load gets cached, matching what's actually
 * visible the instant the app reopens. */
class LocalListCache(@PublishedApi internal val context: Context) {
    @PublishedApi internal val json = Json { ignoreUnknownKeys = true }

    suspend inline fun <reified T> read(key: String): List<T>? {
        val raw = context.emberDataStore.data.first()[stringPreferencesKey(key)] ?: return null
        return runCatching { json.decodeFromString<List<T>>(raw) }.getOrNull()
    }

    suspend inline fun <reified T> write(key: String, value: List<T>) {
        context.emberDataStore.edit { it[stringPreferencesKey(key)] = json.encodeToString(value) }
    }

    /** Same idea as [read]/[write], for the one non-list case (the signed-in user's own
     * profile) — a single object rather than a page of results. */
    suspend inline fun <reified T> readObject(key: String): T? {
        val raw = context.emberDataStore.data.first()[stringPreferencesKey(key)] ?: return null
        return runCatching { json.decodeFromString<T>(raw) }.getOrNull()
    }

    suspend inline fun <reified T> writeObject(key: String, value: T) {
        context.emberDataStore.edit { it[stringPreferencesKey(key)] = json.encodeToString(value) }
    }

    /** Called on sign-out — this cache isn't scoped per-account, so a different user signing in
     * on the same device must never briefly see the previous account's cached lists. */
    suspend fun clearAll() {
        context.emberDataStore.edit {
            it.remove(stringPreferencesKey(KEY_FEED))
            it.remove(stringPreferencesKey(KEY_FRIENDS))
            it.remove(stringPreferencesKey(KEY_ACTIVITY))
            it.remove(stringPreferencesKey(KEY_MEMORIES))
            it.remove(stringPreferencesKey(KEY_PROFILE))
            it.remove(stringPreferencesKey(KEY_LAST_RECIPIENT_IDS))
            it.remove(stringPreferencesKey(KEY_RECIPIENT_LISTS))
            it.remove(stringPreferencesKey(KEY_PENDING_FRIEND_REQUESTS))
            it.remove(stringPreferencesKey(KEY_ACTIVITY_LAST_SEEN))
        }
    }

    companion object {
        const val KEY_FEED = "cache_feed"
        const val KEY_FRIENDS = "cache_friends"
        const val KEY_ACTIVITY = "cache_activity"
        const val KEY_MEMORIES = "cache_memories"
        const val KEY_PROFILE = "cache_profile"

        // A cached copy of the same real, per-account "seen" marker ActivityViewModel already
        // fetches from the backend (ActivityLastSeenDto) — purely an optimistic fast first paint
        // for the nav-dock badge on cold start; the backend fetch that follows right after this
        // still always wins if it disagrees, which is what keeps a reinstall or a second device
        // from ever using a stale local copy to show already-seen activity as new again.
        const val KEY_ACTIVITY_LAST_SEEN = "cache_activity_last_seen"

        // Pending incoming friend requests — read on init the same way KEY_FRIENDS is, so the
        // Friends nav-dock badge shows the right count the instant the app opens rather than
        // waiting on the network fetch that used to be its only source (see FriendsViewModel).
        const val KEY_PENDING_FRIEND_REQUESTS = "cache_pending_friend_requests"

        // Who Camera's recipient picker defaulted to last time a send actually went out — read
        // back on the next cold start so "who I sent to last" survives an app restart instead of
        // resetting to nobody every time. Deliberately only written on a real send (see
        // CameraViewModel.sendCaptured), not on every selection change in the picker, since a
        // selection someone made and then backed out of was never actually confirmed.
        const val KEY_LAST_RECIPIENT_IDS = "cache_last_recipient_ids"

        // A cached copy of the same backend-saved recipient-list shortcuts RecipientPickerViewModel
        // fetches from RecipientListService — purely an optimistic fast first paint; the network
        // fetch that follows right after always wins, which is what lets a list created on a
        // *different* device actually show up here too instead of only ever reflecting whatever
        // this one phone last saw.
        const val KEY_RECIPIENT_LISTS = "cache_recipient_lists"
    }
}
