package com.ember.app.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.UserRepository
import com.ember.app.data.remote.dto.UserProfileDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Result of checking a candidate username against the backend. [Idle] before the user has
 * typed anything meaningful (or after picking back their own current username); [Checking]
 * while a debounced request is in flight; [Taken] carries alternate suggestions the backend
 * already confirmed are free, so the UI never has to guess. */
sealed interface UsernameCheckState {
    data object Idle : UsernameCheckState
    data object Checking : UsernameCheckState
    data object Available : UsernameCheckState
    data class Taken(val suggestions: List<String>) : UsernameCheckState
}

private const val USERNAME_DEBOUNCE_MS = 400L

class MyProfileViewModel(
    private val repository: UserRepository,
    private val onProfileUpdated: (UserProfileDto) -> Unit = {},
) : ViewModel() {

    var profile by mutableStateOf<UserProfileDto?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isUploadingPhoto by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Name popup
    var nameDraft by mutableStateOf("")
        private set
    var isSavingName by mutableStateOf(false)
        private set
    var nameError by mutableStateOf<String?>(null)
        private set

    // Username popup
    var usernameDraft by mutableStateOf("")
        private set
    var usernameCheck by mutableStateOf<UsernameCheckState>(UsernameCheckState.Idle)
        private set
    var isSavingUsername by mutableStateOf(false)
        private set
    var usernameError by mutableStateOf<String?>(null)
        private set
    private var usernameCheckJob: Job? = null

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

    fun openNameEditor() {
        nameDraft = profile?.displayName.orEmpty()
        nameError = null
    }

    fun onNameDraftChange(value: String) {
        nameDraft = value
        nameError = null
    }

    fun saveName(onSaved: () -> Unit) {
        val trimmed = nameDraft.trim()
        if (trimmed.isEmpty()) {
            nameError = "Name can't be empty"
            return
        }
        if (trimmed == profile?.displayName) {
            onSaved()
            return
        }
        viewModelScope.launch {
            isSavingName = true
            nameError = null
            repository.updateProfile(displayName = trimmed).fold(
                onSuccess = {
                    applyProfile(it)
                    onSaved()
                },
                onFailure = { nameError = it.message ?: "Couldn't save your name" },
            )
            isSavingName = false
        }
    }

    fun openUsernameEditor() {
        usernameDraft = profile?.username.orEmpty()
        usernameCheck = UsernameCheckState.Idle
        usernameError = null
        usernameCheckJob?.cancel()
    }

    fun onUsernameDraftChange(value: String) {
        val filtered = value.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(30).lowercase()
        usernameDraft = filtered
        usernameError = null
        usernameCheckJob?.cancel()

        if (filtered.length < 3 || filtered == profile?.username) {
            usernameCheck = UsernameCheckState.Idle
            return
        }

        usernameCheckJob = viewModelScope.launch {
            usernameCheck = UsernameCheckState.Checking
            delay(USERNAME_DEBOUNCE_MS)
            repository.checkUsernameAvailability(filtered).fold(
                onSuccess = { result ->
                    usernameCheck = if (result.available) {
                        UsernameCheckState.Available
                    } else {
                        UsernameCheckState.Taken(result.suggestions)
                    }
                },
                onFailure = { usernameCheck = UsernameCheckState.Idle },
            )
        }
    }

    fun pickSuggestion(name: String) {
        onUsernameDraftChange(name)
    }

    fun saveUsername(onSaved: () -> Unit) {
        val current = profile?.username
        val canSave = usernameDraft == current || usernameCheck is UsernameCheckState.Available
        if (usernameDraft.length < 3) {
            usernameError = "Username must be at least 3 characters"
            return
        }
        if (!canSave) {
            usernameError = "Pick an available username first"
            return
        }
        if (usernameDraft == current) {
            onSaved()
            return
        }
        viewModelScope.launch {
            isSavingUsername = true
            usernameError = null
            repository.updateProfile(username = usernameDraft).fold(
                onSuccess = {
                    applyProfile(it)
                    onSaved()
                },
                onFailure = { usernameError = it.message ?: "Couldn't save your username" },
            )
            isSavingUsername = false
        }
    }

    private fun applyProfile(updated: UserProfileDto) {
        profile = updated
        onProfileUpdated(updated)
    }
}
