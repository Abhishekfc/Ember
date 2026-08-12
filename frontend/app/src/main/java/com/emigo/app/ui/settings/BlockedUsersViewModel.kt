package com.emigo.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.SafetyRepository
import com.emigo.app.data.remote.dto.BlockedUserDto
import kotlinx.coroutines.launch

class BlockedUsersViewModel(private val repository: SafetyRepository) : ViewModel() {

    var blockedUsers by mutableStateOf<List<BlockedUserDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    // Tracks which specific row is mid-unblock, not one screen-wide flag — with several blocked
    // accounts, only the row someone actually tapped should show as in-flight, not the whole list.
    var unblockingUserId by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.getBlockedUsers().fold(
                onSuccess = { blockedUsers = it },
                onFailure = { errorMessage = it.message ?: "Couldn't load blocked accounts" },
            )
            isLoading = false
        }
    }

    fun unblock(userId: String) {
        viewModelScope.launch {
            unblockingUserId = userId
            errorMessage = null
            repository.unblockUser(userId).fold(
                onSuccess = { blockedUsers = blockedUsers.filterNot { it.userId == userId } },
                onFailure = { errorMessage = it.message ?: "Couldn't unblock" },
            )
            unblockingUserId = null
        }
    }
}
