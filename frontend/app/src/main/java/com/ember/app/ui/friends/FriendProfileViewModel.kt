package com.ember.app.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.FriendRepository
import kotlinx.coroutines.launch

class FriendProfileViewModel(
    private val repository: FriendRepository,
    initialSubject: ProfileSubject,
) : ViewModel() {

    var subject by mutableStateOf(initialSubject)
        private set
    var isUpdatingPin by mutableStateOf(false)
        private set
    var isRemoving by mutableStateOf(false)
        private set
    var isAccepting by mutableStateOf(false)
        private set
    var isRejecting by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun togglePin() {
        val friend = (subject as? ProfileSubject.Friend)?.summary ?: return
        viewModelScope.launch {
            isUpdatingPin = true
            errorMessage = null
            repository.setPinned(friend.friendshipId, pinned = !friend.pinnedByMe).fold(
                onSuccess = { subject = ProfileSubject.Friend(friend.copy(pinnedByMe = it.pinnedByMe)) },
                onFailure = { errorMessage = it.message ?: "Couldn't update pin" },
            )
            isUpdatingPin = false
        }
    }

    fun removeFriend(onRemoved: () -> Unit) {
        viewModelScope.launch {
            isRemoving = true
            errorMessage = null
            repository.removeFriend(subject.friendshipId).fold(
                onSuccess = { onRemoved() },
                onFailure = { errorMessage = it.message ?: "Couldn't remove friend" },
            )
            isRemoving = false
        }
    }

    fun acceptRequest(onAccepted: () -> Unit) {
        viewModelScope.launch {
            isAccepting = true
            errorMessage = null
            repository.acceptFriendRequest(subject.friendshipId).fold(
                onSuccess = { onAccepted() },
                onFailure = { errorMessage = it.message ?: "Couldn't accept request" },
            )
            isAccepting = false
        }
    }

    /** No dedicated "decline" endpoint exists server-side — same DELETE /friends/{friendshipId}
     * call "remove friend" already uses (see FriendsViewModel.rejectRequest for the identical
     * reasoning), which the backend already allows regardless of the friendship's status as long
     * as the caller is part of it. */
    fun rejectRequest(onRejected: () -> Unit) {
        viewModelScope.launch {
            isRejecting = true
            errorMessage = null
            repository.removeFriend(subject.friendshipId).fold(
                onSuccess = { onRejected() },
                onFailure = { errorMessage = it.message ?: "Couldn't decline request" },
            )
            isRejecting = false
        }
    }
}
