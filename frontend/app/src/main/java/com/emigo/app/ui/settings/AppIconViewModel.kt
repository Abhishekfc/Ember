package com.emigo.app.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.SubscriptionRepository
import com.emigo.app.data.local.AppIconPreferenceStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppIconViewModel(
    private val store: AppIconPreferenceStore,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    var selectedIcon by mutableStateOf(AppIconKey.DEFAULT)
        private set

    /** Seeded synchronously from the last resolved value (see
     * SubscriptionRepository.isGoldMemberSync), not a hardcoded false — the real check in [init]
     * is a suspend call with a real gap before it resolves, and defaulting to false for that gap
     * flashed a locked icon's own lock badge over a genuine subscriber's unlocked icon for a
     * moment on every cold start before snapping back once the real answer landed. */
    var isGoldMember by mutableStateOf(subscriptionRepository.isGoldMemberSync())
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
        // Outlives any single screen visit (viewModel(...) isn't re-keyed per visit here) —
        // without this, a purchase made from the Ember Gold screen wouldn't unlock icons here
        // until the next full app restart. See SubscriptionRepository.isGoldMemberFlow's own doc
        // comment.
        viewModelScope.launch {
            subscriptionRepository.isGoldMemberFlow.collect { isGoldMember = it }
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
