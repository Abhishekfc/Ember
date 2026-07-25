package com.ember.app.ui.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.AuthRepository
import kotlin.random.Random
import kotlinx.coroutines.launch

/** Every screen the auth flow can be on. [WELCOME] is the true entry point. The new-account path
 * ([REGISTER_EMAIL] then [REGISTER_PASSWORD]) is deliberately one question per screen — this is
 * a brand-new user's very first impression of the app, so it gets the unhurried, focused
 * treatment. [LOGIN] stays a single combined email+password screen: a returning user already
 * knows both, so splitting it the same way would just be an extra tap for no benefit. */
enum class AuthStep { WELCOME, LOGIN, REGISTER_EMAIL, REGISTER_PASSWORD }

private const val MIN_PASSWORD_LENGTH = 8

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    var step by mutableStateOf(AuthStep.WELCOME)
        private set

    /** Which direction the step transition animation should slide — stepping forward slides the
     * new step in from the right (old one exits left), stepping back reverses that, so the
     * motion always matches which way someone would expect the flow to move in physical space. */
    var isMovingForward by mutableStateOf(true)
        private set

    var email by mutableStateOf("")
        private set

    /** The [LOGIN] step's own identifier field — deliberately separate from [email], since it
     * can hold either a real email or a username (see LoginRequest/AuthService.login), while
     * [email] (used by the [REGISTER_EMAIL] step) must always be a real email address. Keeping
     * them as two fields means typing a username in here can never leave [email] holding
     * something [isEmailValid] would reject. */
    var loginIdentifier by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val isEmailValid: Boolean
        get() = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    val isPasswordValid: Boolean
        get() = password.length >= MIN_PASSWORD_LENGTH

    fun onEmailChange(value: String) {
        email = value
        errorMessage = null
    }

    fun onLoginIdentifierChange(value: String) {
        loginIdentifier = value
        errorMessage = null
    }

    fun onPasswordChange(value: String) {
        password = value
        errorMessage = null
    }

    private fun goTo(next: AuthStep) {
        isMovingForward = true
        errorMessage = null
        step = next
    }

    fun goBack() {
        isMovingForward = false
        errorMessage = null
        step = when (step) {
            AuthStep.REGISTER_PASSWORD -> AuthStep.REGISTER_EMAIL
            AuthStep.REGISTER_EMAIL, AuthStep.LOGIN -> AuthStep.WELCOME
            AuthStep.WELCOME -> step
        }
    }

    fun onContinueWithEmailClicked() = goTo(AuthStep.REGISTER_EMAIL)
    fun onSignInClicked() = goTo(AuthStep.LOGIN)
    fun onEmailStepContinue() {
        if (isEmailValid) goTo(AuthStep.REGISTER_PASSWORD)
    }

    /** Real Google sign-in has nothing to verify against yet — the backend only issues/checks
     * its own email+password JWTs (see AuthService), no OAuth token verification exists there at
     * all. Surfaced as a clear, honest "coming soon" rather than either hiding the button or
     * pretending to complete a flow that can't actually finish server-side. */
    fun onGoogleSignInClicked() {
        errorMessage = "Google sign-in is coming soon"
    }

    fun submitLogin(onSuccess: () -> Unit) {
        if (loginIdentifier.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in every field"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.login(loginIdentifier.trim(), password).fold(
                onSuccess = { finishAuth(onSuccess) },
                onFailure = { errorMessage = it.message ?: "Something went wrong" },
            )
            isLoading = false
        }
    }

    /** Only email + password are ever collected here — a real display name/username can be set
     * later from Settings. The placeholder display name/username below exist purely because the
     * register call requires *something* valid for both server-side. */
    fun submitRegister(onSuccess: () -> Unit) {
        if (!isEmailValid || !isPasswordValid) {
            errorMessage = "Please check your details"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val emailPrefix = email.substringBefore("@").filter { it.isLetterOrDigit() }.lowercase()
            val tempUsername = emailPrefix.ifBlank { "member" }.take(20) + Random.nextInt(1000, 9999)
            val tempDisplayName = emailPrefix.ifBlank { "member" }.replaceFirstChar { it.uppercase() }
            repository.register(email.trim(), password, tempDisplayName, tempUsername).fold(
                onSuccess = { finishAuth(onSuccess) },
                onFailure = { errorMessage = it.message ?: "Something went wrong" },
            )
            isLoading = false
        }
    }

    private fun finishAuth(onSuccess: () -> Unit) {
        // Google Password Manager's "Save password?" prompt fires off the password field being
        // non-empty when it disappears from the view tree (i.e. when this screen unmounts on
        // successful auth) — clearing it first, before that happens, leaves nothing for the
        // save-prompt heuristic to act on.
        password = ""
        onSuccess()
    }
}
