package com.emigo.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Caches the signed-in user's display name for instant greeting UI on cold start, before the real
 * profile has been fetched over the network.
 *
 * This used to also hold the session credential itself (an encrypted JWT this backend issued),
 * which is why the class is still named `TokenStore` and why [displayName] still lives in the
 * *un*-encrypted ordinary DataStore alongside everything else in this app, as it always has — see
 * git history for that reasoning if the encrypted file ever needs resurrecting. There's no local
 * credential to store any more: Firebase Authentication owns the session now, persists it in its
 * own storage, and hands back a fresh ID token on demand (see NetworkModule's auth interceptor) —
 * this class has nothing left to do with authentication itself.
 */
class TokenStore(private val context: Context) {

    private val displayNameKey = stringPreferencesKey("display_name")

    val displayName: Flow<String?> = context.emberDataStore.data.map { it[displayNameKey] }

    suspend fun saveDisplayName(displayName: String) {
        context.emberDataStore.edit { it[displayNameKey] = displayName }
    }

    /** Called on sign-out alongside clearing the actual Firebase session
     * ([com.google.firebase.auth.FirebaseAuth.signOut]) — this only ever held this account's own
     * cached display name, which must not leak into whatever's shown before a different account's
     * profile has loaded on this same device. */
    suspend fun clear() {
        context.emberDataStore.edit { it.remove(displayNameKey) }
    }
}
