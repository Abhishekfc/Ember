package com.emigo.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.emigo.app.ui.settings.AppIconKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persists which launcher icon the user picked so it survives app restarts — this is purely a
 * record of the choice, not the mechanism that applies it (see AppIconSwitcher for the actual
 * PackageManager toggle, which already reflects the choice at the OS level the instant it's
 * made; this store exists so the App Icon screen itself can show the right one as selected the
 * next time it's opened, without asking PackageManager to enumerate component states). */
class AppIconPreferenceStore(private val context: Context) {

    private val appIconKeyPref = stringPreferencesKey("selected_app_icon")

    suspend fun selectedIcon(): AppIconKey = context.emberDataStore.data.map { prefs ->
        prefs[appIconKeyPref]?.let { stored ->
            runCatching { AppIconKey.valueOf(stored) }.getOrNull()
        } ?: AppIconKey.DEFAULT
    }.first()

    suspend fun save(iconKey: AppIconKey) {
        context.emberDataStore.edit { it[appIconKeyPref] = iconKey.name }
    }

    /** Called on sign-out, alongside every other per-account cache — icon choice has no backend
     * representation, it's purely a local, device-scoped preference, so without this a different
     * account signing in on the same device would inherit whatever the previous account picked. */
    suspend fun clear() {
        context.emberDataStore.edit { it.remove(appIconKeyPref) }
    }
}
