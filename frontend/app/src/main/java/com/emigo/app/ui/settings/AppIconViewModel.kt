package com.emigo.app.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.SubscriptionRepository
import com.emigo.app.data.local.AppIconPreferenceStore
import kotlinx.coroutines.launch

class AppIconViewModel(
    private val store: AppIconPreferenceStore,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    var selectedIcon by mutableStateOf(AppIconKey.DEFAULT)
        private set

    /** Defaults to false (not Gold) until the real check resolves — same reasoning as
     * ThemeViewModel's own isGoldMember: a locked icon should never briefly look selectable
     * before snapping back once the real answer lands. */
    var isGoldMember by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val persisted = store.selectedIcon()
            isGoldMember = subscriptionRepository.isGoldMemberOrLastKnown()
            // An icon saved while subscribed shouldn't keep applying for free forever once that
            // subscription lapses — same reasoning ThemeViewModel.reload applies to a persisted
            // theme, and the same reason selectIcon below re-checks before persisting a new one.
            selectedIcon = if (persisted.locked && !isGoldMember) AppIconKey.DEFAULT else persisted
        }
    }

    /** [context] is the caller's own — always `LocalContext.current.applicationContext` from
     * AppIconScreen — rather than something this ViewModel holds itself, so nothing here can leak
     * an Activity reference past its own lifecycle. */
    fun selectIcon(context: Context, iconKey: AppIconKey) {
        // Belt-and-suspenders alongside AppIconScreen's own gating on the Apply button — this is
        // the one place a locked icon could actually get applied, so it guards here too rather
        // than trusting the UI layer alone.
        if (iconKey.locked && !isGoldMember) return
        AppIconSwitcher.apply(context, iconKey)
        selectedIcon = iconKey
        viewModelScope.launch { store.save(iconKey) }
    }
}
