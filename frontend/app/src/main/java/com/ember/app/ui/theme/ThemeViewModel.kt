package com.ember.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.local.ThemePreferenceStore
import kotlinx.coroutines.launch

class ThemeViewModel(private val store: ThemePreferenceStore) : ViewModel() {

    var selectedTheme by mutableStateOf(ThemeKey.EMBER)
        private set

    init {
        viewModelScope.launch {
            selectedTheme = store.currentTheme()
        }
    }

    fun selectTheme(themeKey: ThemeKey) {
        selectedTheme = themeKey
        viewModelScope.launch { store.save(themeKey) }
    }
}
