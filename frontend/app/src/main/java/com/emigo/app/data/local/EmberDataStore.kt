package com.emigo.app.data.local

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Single shared preferences file backing every local store (auth token, theme choice, etc). */
val Context.emberDataStore by preferencesDataStore(name = "ember_prefs")
