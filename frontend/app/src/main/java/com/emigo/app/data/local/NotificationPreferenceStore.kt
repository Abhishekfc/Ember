package com.emigo.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The Settings screen's notifications on/off preference.
 *
 * This was written before push existed ("push delivery isn't wired on this client yet, but the
 * preference is persisted now so it can gate FCM the moment that lands") — push then landed and
 * nothing was ever wired to read it, so the toggle saved a value and changed nothing. Turning
 * notifications off still showed every notification. [enabledNow] is what
 * EmberFirebaseMessagingService checks before posting one.
 *
 * Note this only stops the notification being *shown*. The server still sends the message, which
 * is what keeps the widget and feed syncing in the background — those aren't notifications and
 * aren't what this toggle is about.
 */
class NotificationPreferenceStore(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("notifications_enabled")

    val enabled: Flow<Boolean> = context.emberDataStore.data.map { it[enabledKey] ?: true }

    /** One-shot read for callers that aren't collecting a flow — the push handler runs outside any
     * UI lifecycle and just needs the current answer before deciding whether to post. Defaults to
     * true on any read failure: the safe default for a notification the user hasn't opted out of
     * is to show it, and silently swallowing notifications because a preference read hiccuped
     * would be far harder to notice than one extra notification. */
    suspend fun enabledNow(): Boolean = runCatching { enabled.first() }.getOrDefault(true)

    suspend fun save(value: Boolean) {
        context.emberDataStore.edit { it[enabledKey] = value }
    }

    /** Called on sign-out alongside every other per-account preference (see MainActivity's own
     * onSignOut) — this lives in the same shared, non-account-scoped DataStore as everything
     * else here, so without an explicit clear a different account signing in on this device
     * would silently inherit whatever the previous account had chosen. */
    suspend fun clear() {
        context.emberDataStore.edit { it.remove(enabledKey) }
    }
}
