package com.ember.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ember.app.ui.theme.ThemeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persists the user's chosen theme so it survives app restarts. */
class ThemePreferenceStore(private val context: Context) {

    private val themeKeyPref = stringPreferencesKey("selected_theme")

    val selectedTheme: Flow<ThemeKey> = context.emberDataStore.data.map { prefs ->
        prefs[themeKeyPref]?.let { stored ->
            runCatching { ThemeKey.valueOf(stored) }.getOrNull()
        } ?: ThemeKey.EMBER
    }

    suspend fun currentTheme(): ThemeKey = selectedTheme.first()

    suspend fun save(themeKey: ThemeKey) {
        context.emberDataStore.edit { it[themeKeyPref] = themeKey.name }
    }
}
