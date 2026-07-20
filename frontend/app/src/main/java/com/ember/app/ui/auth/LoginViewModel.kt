package com.ember.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.AuthRepository
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, REGISTER }

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    var mode by mutableStateOf(AuthMode.LOGIN)
        private set
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var displayName by mutableStateOf("")
        private set
    var username by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onEmailChange(value: String) { email = value }
    fun onPasswordChange(value: String) { password = value }
    fun onDisplayNameChange(value: String) { displayName = value }
    fun onUsernameChange(value: String) { username = value.filter { it.isLetterOrDigit() || it == '_' || it == '.' } }

    fun toggleMode() {
        mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
        errorMessage = null
    }

    fun submit(onSuccess: () -> Unit) {
        val registerFieldsMissing = mode == AuthMode.REGISTER && (displayName.isBlank() || username.isBlank())
        if (email.isBlank() || password.isBlank() || registerFieldsMissing) {
            errorMessage = "Please fill in every field"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val result = if (mode == AuthMode.LOGIN) {
                repository.login(email.trim(), password)
            } else {
                repository.register(email.trim(), password, displayName.trim(), username.trim().lowercase())
            }
            isLoading = false
            result.fold(
                onSuccess = {
                    // Google Password Manager's "Save password?" prompt fires off the password
                    // field being non-empty when it disappears from the view tree (i.e. when
                    // this screen unmounts on successful login) — clearing it first, before
                    // that happens, leaves nothing for the save-prompt heuristic to act on.
                    // (KeyboardType.Text on the field and disabling Autofill services outright
                    // weren't enough on their own to stop it appearing on every login.)
                    password = ""
                    onSuccess()
                },
                onFailure = { errorMessage = it.message ?: "Something went wrong" },
            )
        }
    }
}
