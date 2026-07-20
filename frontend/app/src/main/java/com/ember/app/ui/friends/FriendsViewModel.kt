package com.ember.app.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.FriendRepository
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.PendingFriendRequestDto
import kotlinx.coroutines.launch

class FriendsViewModel(private val repository: FriendRepository) : ViewModel() {

    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set
    var pendingRequests by mutableStateOf<List<PendingFriendRequestDto>>(emptyList())
        private set
    var acceptingRequestIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var searchQuery by mutableStateOf("")
        private set

    val filteredFriends: List<FriendSummaryDto>
        get() = if (searchQuery.isBlank()) {
            friends
        } else {
            friends.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.username.contains(searchQuery, ignoreCase = true)
            }
        }

    init {
        loadFriends()
    }

    fun onSearchQueryChange(value: String) {
        searchQuery = value
    }

    fun loadFriends() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.getFriends().fold(
                onSuccess = { friends = it },
                onFailure = { errorMessage = it.message ?: "Couldn't load your friends" },
            )
            // Requests load is best-effort: a failure here shouldn't blank the friends list.
            repository.getPendingRequests().onSuccess { pendingRequests = it }
            isLoading = false
        }
    }

    fun acceptRequest(request: PendingFriendRequestDto) {
        if (request.friendshipId in acceptingRequestIds) return
        viewModelScope.launch {
            acceptingRequestIds = acceptingRequestIds + request.friendshipId
            repository.acceptFriendRequest(request.friendshipId).fold(
                onSuccess = { newFriend ->
                    pendingRequests = pendingRequests.filterNot { it.friendshipId == request.friendshipId }
                    friends = friends + newFriend
                },
                onFailure = { errorMessage = it.message ?: "Couldn't accept request" },
            )
            acceptingRequestIds = acceptingRequestIds - request.friendshipId
        }
    }
}
