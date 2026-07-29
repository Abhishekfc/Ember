package com.ember.app.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ember.app.data.local.emberDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Which friends (if any) a Gold subscriber has chosen to always feature in the home-screen
 * widget, instead of the default "whoever sent the most recent photo" every account starts with.
 * An empty set means no custom choice has been made — [WidgetPhotoSync] treats that identically
 * to a free account, so this is also what a lapsed subscription reverts to.
 *
 * Also holds the last-known subscription status ([cachedIsGoldMember]), refreshed once per app
 * open (see MainActivity) rather than on every widget sync. That single per-session check — the
 * same shape CameraViewModel/ThemeViewModel already use for their own Gold checks, not a new
 * pattern — is what lets [WidgetPhotoSync]'s background refresh (WidgetUpdateWorker) and push
 * handling honor a featured-friend choice without a network call of their own on every sync,
 * while still self-healing within one app session if a subscription has actually lapsed: the
 * moment the app is reopened, the fresh check either confirms the choice or clears it. */
class WidgetPreferenceStore(private val context: Context) {

    private val featuredFriendIdsKey = stringSetPreferencesKey("widget_featured_friend_ids")
    private val cachedIsGoldMemberKey = booleanPreferencesKey("widget_cached_is_gold_member")

    val featuredFriendIds: Flow<Set<String>> = context.emberDataStore.data.map { prefs ->
        prefs[featuredFriendIdsKey] ?: emptySet()
    }

    suspend fun currentFeaturedFriendIds(): Set<String> = featuredFriendIds.first()

    suspend fun setFeaturedFriendIds(friendIds: Set<String>) {
        context.emberDataStore.edit { it[featuredFriendIdsKey] = friendIds }
    }

    /** Read by [WidgetPhotoSync]'s background/push paths in place of a live subscription check —
     * see this class's own doc comment for why. Written once per app open (MainActivity), never
     * inside a sync itself. */
    suspend fun cachedIsGoldMember(): Boolean =
        context.emberDataStore.data.first()[cachedIsGoldMemberKey] ?: false

    suspend fun setCachedIsGoldMember(isGoldMember: Boolean) {
        context.emberDataStore.edit { it[cachedIsGoldMemberKey] = isGoldMember }
    }

    /** Called on sign-out, alongside WidgetPhotoStore.clear() — a different account signing in on
     * the same device must never inherit the previous account's featured-friend choice or cached
     * Gold status. */
    suspend fun clear() {
        context.emberDataStore.edit {
            it.remove(featuredFriendIdsKey)
            it.remove(cachedIsGoldMemberKey)
        }
    }
}
