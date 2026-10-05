package com.emigo.app.ui.settings

import com.emigo.app.R
import com.emigo.app.StringProvider

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.SafetyRepository
import com.emigo.app.data.remote.dto.BlockedUserDto
import kotlinx.coroutines.launch

class BlockedUsersViewModel(
    private val strings: StringProvider,
    private val repository: SafetyRepository,
) : ViewModel() {

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
                onFailure = { errorMessage = it.message ?: strings.get(R.string.error_load_blocked) },
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
                onFailure = { errorMessage = it.message ?: strings.get(R.string.error_unblock) },
            )
            unblockingUserId = null
        }
    }
}
