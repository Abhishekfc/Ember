package com.ember.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.FriendRepository
import com.ember.app.data.SubscriptionRepository
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.widget.WidgetPreferenceStore
import kotlinx.coroutines.launch

/** A generously high ceiling for "give me every friend to choose from" — not a real pagination
 * page size, matching the same constant every other friend-picker in this app uses. */
private const val WIDGET_FRIENDS_LIMIT = 500

class WidgetSettingsViewModel(
    private val friendRepository: FriendRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val widgetPreferenceStore: WidgetPreferenceStore,
) : ViewModel() {

    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Defaults to false until the real check resolves — same reasoning as every other Gold
     * check in this app (CameraViewModel/ThemeViewModel): never let a gated action briefly look
     * available before snapping shut once the real answer lands. */
    var isGoldMember by mutableStateOf(false)
        private set

    /** Staged, not yet persisted — mirrors ThemeScreen's own stage-then-apply shape. Starts from
     * whatever's already saved so reopening this screen shows the current choice, not a blank
     * slate. */
    var selectedFriendIds by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        viewModelScope.launch {
            selectedFriendIds = widgetPreferenceStore.currentFeaturedFriendIds()
        }
        viewModelScope.launch {
            isGoldMember = subscriptionRepository.isGoldMemberOrLastKnown()
        }
        loadFriends()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            friendRepository.getFriends(limit = WIDGET_FRIENDS_LIMIT).fold(
                onSuccess = { page -> friends = page.items },
                onFailure = { errorMessage = it.message ?: "Couldn't load your friends" },
            )
            isLoading = false
        }
    }

    fun toggleSelected(friendId: String) {
        selectedFriendIds = if (friendId in selectedFriendIds) selectedFriendIds - friendId else selectedFriendIds + friendId
    }

    /** "Anyone" — the same default behavior every account, free or Gold, already starts with. */
    fun clearSelection() {
        selectedFriendIds = emptySet()
    }

    /** Persisting an empty selection is always free (see [clearSelection]'s own doc comment); a
     * non-empty one is the Gold-gated action. WidgetSettingsScreen's own Save button already only
     * calls this once [isGoldMember] is confirmed true, redirecting to Ember Gold otherwise — this
     * guards here too rather than trusting the UI layer alone. */
    fun save() {
        if (selectedFriendIds.isNotEmpty() && !isGoldMember) return
        viewModelScope.launch { widgetPreferenceStore.setFeaturedFriendIds(selectedFriendIds) }
    }
}
