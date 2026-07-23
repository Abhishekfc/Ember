package com.ember.app.ui.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.FriendRepository
import com.ember.app.data.remote.dto.FriendSummaryDto
import kotlinx.coroutines.launch

/** A generously high ceiling for "give me every friend to choose a recipient from" — not a real
 * pagination page size, just far above any real user's friend count. */
private const val RECIPIENT_PICKER_FRIENDS_LIMIT = 500

class RecipientPickerViewModel(
    private val repository: FriendRepository,
    initialSelectedFriendIds: Set<String>,
) : ViewModel() {

    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var selectedFriendIds by mutableStateOf(initialSelectedFriendIds)
        private set

    init {
        loadFriends()
    }

    fun loadFriends() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.getFriends(limit = RECIPIENT_PICKER_FRIENDS_LIMIT).fold(
                onSuccess = { page -> friends = page.items },
                onFailure = { errorMessage = it.message ?: "Couldn't load your friends" },
            )
            isLoading = false
        }
    }

    fun toggleSelected(friendId: String) {
        selectedFriendIds = if (friendId in selectedFriendIds) {
            selectedFriendIds - friendId
        } else {
            selectedFriendIds + friendId
        }
    }

    fun setSelection(ids: Set<String>) {
        selectedFriendIds = ids
    }
}
