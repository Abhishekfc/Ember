package com.ember.app.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.UserRepository
import com.ember.app.data.remote.dto.UserProfileDto
import kotlinx.coroutines.launch
import java.io.File

class MyProfileViewModel(
    private val repository: UserRepository,
    private val onProfileUpdated: (UserProfileDto) -> Unit = {},
) : ViewModel() {

    var profile by mutableStateOf<UserProfileDto?>(null)
        private set
    var displayNameInput by mutableStateOf("")
        private set
    var usernameInput by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var isUploadingPhoto by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val hasChanges: Boolean
        get() {
            val current = profile ?: return false
            return displayNameInput.trim() != current.displayName || usernameInput.trim() != current.username
        }

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            isLoading = true
            repository.getMyProfile().fold(
                onSuccess = { applyProfile(it) },
                onFailure = { errorMessage = it.message ?: "Couldn't load your profile" },
            )
            isLoading = false
        }
    }

    fun onDisplayNameChange(value: String) {
        displayNameInput = value
    }

    fun onUsernameChange(value: String) {
        usernameInput = value.filter { it.isLetterOrDigit() || it == '_' || it == '.' }
    }

    fun save() {
        if (!hasChanges || displayNameInput.isBlank() || usernameInput.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            errorMessage = null
            repository.updateProfile(displayNameInput.trim(), usernameInput.trim().lowercase()).fold(
                onSuccess = { applyProfile(it) },
                onFailure = { errorMessage = it.message ?: "Couldn't save your changes" },
            )
            isSaving = false
        }
    }

    fun uploadPhoto(file: File) {
        viewModelScope.launch {
            isUploadingPhoto = true
            errorMessage = null
            repository.uploadProfilePhoto(file).fold(
                onSuccess = { applyProfile(it) },
                onFailure = { errorMessage = it.message ?: "Couldn't update your profile photo" },
            )
            isUploadingPhoto = false
        }
    }

    private fun applyProfile(updated: UserProfileDto) {
        profile = updated
        displayNameInput = updated.displayName
        usernameInput = updated.username
        onProfileUpdated(updated)
    }
}
