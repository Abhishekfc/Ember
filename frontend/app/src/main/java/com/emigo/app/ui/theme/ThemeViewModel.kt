package com.emigo.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.SubscriptionRepository
import com.emigo.app.data.local.ThemePreferenceStore
import kotlinx.coroutines.launch

class ThemeViewModel(
    private val store: ThemePreferenceStore,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    // Seeded synchronously from the last resolved value (persisted theme, already gated for
    // Gold status — see ThemeChip's own doc comment on lastEffectiveThemeSync) rather than a
    // hardcoded Ember default. store.currentTheme() and the Gold check below are both suspend
    // calls with a real gap before they resolve; defaulting to Ember for that gap made every
    // cold start visibly flash from Ember to the real theme a moment later.
    var selectedTheme by mutableStateOf(store.lastEffectiveThemeSync())
        private set

    /** Defaults to false (not Gold) until the real check resolves — same reasoning as
     * CameraViewModel's own isGoldMember: a locked theme should never briefly look applied
     * before snapping back once the real answer lands. */
    var isGoldMember by mutableStateOf(false)
        private set

    init {
        reload()
    }

    /** Re-runs the same persisted-theme + Gold-status resolution [init] does — this ViewModel is
     * constructed once, at the very top of the whole Compose tree (see MainActivity), and outlives
     * any single signed-in account for the entire app session, so signing into a *different*
     * account never naturally re-triggers this on its own the way a fresh ViewModel would. Called
     * again from MainActivity's own onAuthenticated, right after a successful login, so the newly
     * signed-in account's own saved theme (and real Gold status) actually gets applied instead of
     * silently keeping whatever [reset] (or the previous account) last left this showing. */
    fun reload() {
        viewModelScope.launch {
            // Deliberately sequential in one coroutine, not two separate launches racing each
            // other — the persisted-theme fallback below needs the *real, final* Gold status,
            // not whichever of the two happens to resolve first. Getting this wrong would revert
            // a genuine subscriber's premium theme back to Ember on every single app restart,
            // whenever the persisted-theme read happened to land before the subscription check.
            val persisted = store.currentTheme()
            isGoldMember = subscriptionRepository.isGoldMemberOrLastKnown()
            // A theme saved while subscribed shouldn't keep applying for free forever once that
            // subscription lapses.
            val effective = if (persisted.locked && !isGoldMember) ThemeKey.DEFAULT else persisted
            selectedTheme = effective
            store.saveEffectiveThemeSync(effective)
        }
    }

    /** Called from MainActivity's onSignOut, alongside its own themePreferenceStore.clear() —
     * that clears the *persisted* theme so a different account signing in later doesn't inherit
     * it, but has no effect on this ViewModel's own already-resolved, in-memory [selectedTheme]/
     * [isGoldMember], since (see [reload]'s own doc comment) this instance is never recreated on
     * sign-out. Without this, a Gold-gated theme selected by the outgoing account kept visibly
     * applying — correctly cleared on disk, but still showing on screen — until the process was
     * killed and relaunched from scratch. */
    fun reset() {
        selectedTheme = ThemeKey.DEFAULT
        isGoldMember = false
    }

    fun selectTheme(themeKey: ThemeKey) {
        // Belt-and-suspenders alongside ThemeScreen's own gating on the Apply button — this is
        // the one place a locked theme could actually get persisted, so it guards here too rather
        // than trusting the UI layer alone.
        if (themeKey.locked && !isGoldMember) return
        selectedTheme = themeKey
        viewModelScope.launch {
            store.save(themeKey)
            store.saveEffectiveThemeSync(themeKey)
        }
    }
}
