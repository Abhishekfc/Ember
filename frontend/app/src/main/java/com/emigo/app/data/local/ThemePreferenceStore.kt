package com.emigo.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.emigo.app.ui.theme.ThemeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persists the user's chosen theme so it survives app restarts. */
class ThemePreferenceStore(private val context: Context) {

    private val themeKeyPref = stringPreferencesKey("selected_theme")

    // Plain SharedPreferences, not DataStore — read synchronously so the very first composed
    // frame at cold start can already show the right theme. DataStore's own read (selectedTheme
    // below) and the Gold-status check it's gated on (see ThemeViewModel.init) are both suspend
    // functions with a real gap before they resolve; seeding straight from those would default to
    // Ember for that whole gap and then visibly flash to the real theme a moment later. This
    // holds the last *effective* theme (already gated for Gold status, not just the raw saved
    // choice) — ThemeViewModel.init is the only writer, right after it resolves that gating.
    private val syncPrefs = context.getSharedPreferences("ember_theme_sync", Context.MODE_PRIVATE)
    private val syncThemeKeyPref = "effective_theme"

    val selectedTheme: Flow<ThemeKey> = context.emberDataStore.data.map { prefs ->
        prefs[themeKeyPref]?.let { stored ->
            runCatching { ThemeKey.valueOf(stored) }.getOrNull()
        } ?: ThemeKey.DEFAULT
    }

    suspend fun currentTheme(): ThemeKey = selectedTheme.first()

    suspend fun save(themeKey: ThemeKey) {
        context.emberDataStore.edit { it[themeKeyPref] = themeKey.name }
    }

    fun lastEffectiveThemeSync(): ThemeKey =
        syncPrefs.getString(syncThemeKeyPref, null)
            ?.let { runCatching { ThemeKey.valueOf(it) }.getOrNull() }
            ?: ThemeKey.DEFAULT

    fun saveEffectiveThemeSync(themeKey: ThemeKey) {
        syncPrefs.edit().putString(syncThemeKeyPref, themeKey.name).apply()
    }

    /** Called on sign-out, alongside every other per-account cache (see MainActivity's own
     * onSignOut) — theme has no backend representation of its own, it's purely a local,
     * device-scoped preference, so without this a different account signing in on the same
     * device would inherit whatever the previous account had chosen, Gold-gated theme included. */
    suspend fun clear() {
        context.emberDataStore.edit { it.remove(themeKeyPref) }
        syncPrefs.edit().remove(syncThemeKeyPref).apply()
    }
}
