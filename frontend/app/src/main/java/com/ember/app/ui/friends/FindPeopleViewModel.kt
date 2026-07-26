package com.ember.app.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.FriendRepository
import com.ember.app.data.remote.dto.FriendSearchResultDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300L
private const val MIN_QUERY_LENGTH = 2

class FindPeopleViewModel(private val repository: FriendRepository) : ViewModel() {

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<FriendSearchResultDto>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        query = value
        searchJob?.cancel()
        if (value.trim().length < MIN_QUERY_LENGTH) {
            results = emptyList()
            isSearching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            isSearching = true
            errorMessage = null
            repository.searchUsers(value.trim()).fold(
                onSuccess = { results = it },
                onFailure = { errorMessage = it.message ?: "Search failed" },
            )
            isSearching = false
        }
    }

    fun sendRequest(userId: String) {
        viewModelScope.launch {
            repository.sendFriendRequest(userId).fold(
                onSuccess = { sent ->
                    results = results.map { r ->
                        if (r.userId == userId) {
                            r.copy(requested = true, friendshipId = sent.friendshipId, isPendingFromMe = true)
                        } else {
                            r
                        }
                    }
                },
                onFailure = { errorMessage = it.message ?: "Couldn't send request" },
            )
        }
    }

    /** Only ever called for a result with [FriendSearchResultDto.isPendingFromMe] true — the
     * screen itself gates on that, but this reuses the exact same DELETE endpoint that removing
     * an already-accepted friendship uses (see FriendRepository.removeFriend). The backend allows
     * that regardless of the friendship's current status, as long as the caller is part of it. */
    fun cancelRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.removeFriend(friendshipId).fold(
                onSuccess = {
                    results = results.map { r ->
                        if (r.friendshipId == friendshipId) {
                            r.copy(requested = false, friendshipId = null, isPendingFromMe = false)
                        } else {
                            r
                        }
                    }
                },
                onFailure = { errorMessage = it.message ?: "Couldn't cancel request" },
            )
        }
    }
}
