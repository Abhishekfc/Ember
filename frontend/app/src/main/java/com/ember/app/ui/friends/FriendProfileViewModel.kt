package com.ember.app.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.FriendRepository
import com.ember.app.data.remote.dto.FriendSummaryDto
import kotlinx.coroutines.launch

class FriendProfileViewModel(
    private val repository: FriendRepository,
    initialFriend: FriendSummaryDto,
) : ViewModel() {

    var friend by mutableStateOf(initialFriend)
        private set
    var isUpdatingPin by mutableStateOf(false)
        private set
    var isRemoving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun togglePin() {
        viewModelScope.launch {
            isUpdatingPin = true
            errorMessage = null
            repository.setPinned(friend.friendshipId, pinned = !friend.pinnedByMe).fold(
                onSuccess = { friend = friend.copy(pinnedByMe = it.pinnedByMe) },
                onFailure = { errorMessage = it.message ?: "Couldn't update pin" },
            )
            isUpdatingPin = false
        }
    }

    fun removeFriend(onRemoved: () -> Unit) {
        viewModelScope.launch {
            isRemoving = true
            errorMessage = null
            repository.removeFriend(friend.friendshipId).fold(
                onSuccess = { onRemoved() },
                onFailure = { errorMessage = it.message ?: "Couldn't remove friend" },
            )
            isRemoving = false
        }
    }
}
