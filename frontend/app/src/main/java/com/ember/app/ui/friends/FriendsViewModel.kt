package com.ember.app.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.FriendRepository
import com.ember.app.data.remote.dto.FriendSummaryDto
import kotlinx.coroutines.launch

class FriendsViewModel(private val repository: FriendRepository) : ViewModel() {

    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
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
            isLoading = false
        }
    }
}
