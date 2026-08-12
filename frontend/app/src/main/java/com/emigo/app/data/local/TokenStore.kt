package com.emigo.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Persists the JWT issued at login/register (plus the signed-in user's display name for
 * greeting UI) so the app stays signed in across launches.
 *
 * The token specifically lives in its own Keystore-backed [EncryptedSharedPreferences] file, not
 * the plain DataStore used for [displayName] and everything else in this app — it's the entire
 * authentication credential for the account, and DataStore Preferences persists as an
 * unencrypted file that's readable by anyone with root/`run-as` access or extractable via
 * `adb backup` (see AndroidManifest's `dataExtractionRules`, which additionally excludes this
 * file by name as defense in depth). `displayName` carries no such risk and stays in the
 * ordinary cache. */
class TokenStore(private val context: Context) {

    private val displayNameKey = stringPreferencesKey("display_name")

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ember_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val displayName: Flow<String?> = context.emberDataStore.data.map { it[displayNameKey] }

    suspend fun currentToken(): String? = withContext(Dispatchers.IO) {
        encryptedPrefs.getString(TOKEN_KEY, null)
    }

    suspend fun save(token: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(TOKEN_KEY, token).apply()
        }
    }

    suspend fun saveDisplayName(displayName: String) {
        context.emberDataStore.edit { it[displayNameKey] = displayName }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().remove(TOKEN_KEY).apply()
        }
        context.emberDataStore.edit { it.remove(displayNameKey) }
    }

    companion object {
        private const val TOKEN_KEY = "jwt_token"
    }
}
