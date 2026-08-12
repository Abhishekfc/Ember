package com.emigo.app.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.UserRepository
import com.emigo.app.data.local.LocalListCache
import com.emigo.app.data.remote.dto.UserProfileDto
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
    private val localCache: LocalListCache,
    initialProfile: UserProfileDto? = null,
    private val onProfileUpdated: (UserProfileDto) -> Unit = {},
) : ViewModel() {

    // Seeded from the same synchronous cold-start cache read Home itself uses (see
    // InitialHomeCache in HomeViewModel.kt) so a returning, already-signed-in user sees their
    // own name/username/photo the instant this screen opens, network round trip or not.
    var profile by mutableStateOf(initialProfile)
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

    // Bumped every time nameError is set to a fresh failure (not on clear) — the toast's own
    // auto-dismiss LaunchedEffect keys off this instead of the error string itself. Two failures
    // in a row can carry the exact same message ("Name can't be empty" twice), and Compose's
    // snapshot state skips notifying observers when a new value structurally equals the old one —
    // keying on the string alone meant a second identical failure inside the first toast's 2.6s
    // window silently failed to restart the dismiss timer or replay the toast.
    var nameErrorNonce by mutableStateOf(0)
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
    // See nameErrorNonce's doc comment — same reasoning, same fix.
    var usernameErrorNonce by mutableStateOf(0)
        private set
    private var usernameCheckJob: Job? = null

    // Password popup — three plain drafts, validated client-side (new/confirm match, new is at
    // least 8 characters, matching the backend's own @Size(min=8) on ChangePasswordRequest)
    // before ever calling the network, so a typo is caught instantly rather than round-tripping.
    var currentPasswordDraft by mutableStateOf("")
        private set
    var newPasswordDraft by mutableStateOf("")
        private set
    var confirmPasswordDraft by mutableStateOf("")
        private set
    var isSavingPassword by mutableStateOf(false)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    // See nameErrorNonce's doc comment — same reasoning, same fix. This is the field most likely
    // to actually hit the collision: retrying the same wrong current password produces the exact
    // same message every time.
    var passwordErrorNonce by mutableStateOf(0)
        private set

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            isLoading = true
            repository.getMyProfile().fold(
                onSuccess = {
                    errorMessage = null
                    applyProfile(it)
                },
                onFailure = {
                    // A logged-in user going offline should keep seeing their own cached
                    // picture/name/username exactly as-is, not a "couldn't connect" state where
                    // their profile used to be — only surface the error when there's truly
                    // nothing cached yet to fall back on (mirrors Home's own empty-state rule).
                    if (profile == null) errorMessage = it.message ?: "Couldn't load your profile"
                },
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

    /** Only ever called by the toast's own auto-dismiss timer (see DialogTopToast's call sites) —
     * clearing on the next keystroke is already handled by onNameDraftChange above. */
    fun clearNameError() {
        nameError = null
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
            nameErrorNonce++
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
                onFailure = {
                    nameError = it.message ?: "Couldn't save your name"
                    nameErrorNonce++
                },
            )
            isSavingName = false
        }
    }

    fun clearUsernameError() {
        usernameError = null
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
            usernameErrorNonce++
            return
        }
        if (!canSave) {
            usernameError = "Pick an available username first"
            usernameErrorNonce++
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
                onFailure = {
                    usernameError = it.message ?: "Couldn't save your username"
                    usernameErrorNonce++
                },
            )
            isSavingUsername = false
        }
    }

    fun clearPasswordError() {
        passwordError = null
    }

    fun openPasswordEditor() {
        currentPasswordDraft = ""
        newPasswordDraft = ""
        confirmPasswordDraft = ""
        passwordError = null
    }

    fun onCurrentPasswordDraftChange(value: String) {
        currentPasswordDraft = value
        passwordError = null
    }

    fun onNewPasswordDraftChange(value: String) {
        newPasswordDraft = value
        passwordError = null
    }

    fun onConfirmPasswordDraftChange(value: String) {
        confirmPasswordDraft = value
        passwordError = null
    }

    fun savePassword(onSaved: () -> Unit) {
        if (currentPasswordDraft.isEmpty()) {
            passwordError = "Enter your current password"
            passwordErrorNonce++
            return
        }
        if (newPasswordDraft.length < 8) {
            passwordError = "New password must be at least 8 characters"
            passwordErrorNonce++
            return
        }
        if (newPasswordDraft != confirmPasswordDraft) {
            passwordError = "New passwords don't match"
            passwordErrorNonce++
            return
        }
        viewModelScope.launch {
            isSavingPassword = true
            passwordError = null
            repository.changePassword(currentPasswordDraft, newPasswordDraft).fold(
                onSuccess = { onSaved() },
                onFailure = {
                    passwordError = it.message ?: "Couldn't change your password"
                    passwordErrorNonce++
                },
            )
            isSavingPassword = false
        }
    }

    private fun applyProfile(updated: UserProfileDto) {
        profile = updated
        onProfileUpdated(updated)
        viewModelScope.launch { localCache.writeObject(LocalListCache.KEY_PROFILE, updated) }
    }
}
