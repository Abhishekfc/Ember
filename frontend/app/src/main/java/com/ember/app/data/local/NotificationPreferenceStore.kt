package com.ember.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Local notifications on/off preference. Push delivery isn't wired on this client yet, but the
 * preference is persisted now so it can gate FCM the moment that lands. */
class NotificationPreferenceStore(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("notifications_enabled")

    val enabled: Flow<Boolean> = context.emberDataStore.data.map { it[enabledKey] ?: true }

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
