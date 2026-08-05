package com.ember.app.ui.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.AuthRepository
import com.ember.app.ui.profile.UsernameCheckState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Every screen the auth flow can be on. [WELCOME] is the true entry point. The new-account path
 * ([REGISTER_EMAIL] then [REGISTER_PASSWORD] then [REGISTER_NAME] then [REGISTER_USERNAME] then
 * [REGISTER_SHARING]) is deliberately one question per screen — this is a brand-new user's very
 * first impression of the app, so it gets the unhurried, focused treatment. [LOGIN] stays a
 * single combined email+password screen: a returning user already knows both, so splitting it
 * the same way would just be an extra tap for no benefit.
 *
 * The account is only ever actually created once every field is in hand — email, password,
 * name, AND a confirmed-available username — via one [AuthRepository.register] call fired from
 * [submitUsername]. Nothing server-side exists before that: [REGISTER_PASSWORD]/[REGISTER_NAME]
 * only validate and hold their values locally, and the live username-availability check on
 * [REGISTER_USERNAME] goes through [AuthRepository.checkUsernameAvailability] — a public,
 * pre-auth endpoint (`GET /auth/username-availability`), since there's no account/token yet to
 * authenticate the usual `/users/me/...` version Settings' own profile editor uses. (An earlier
 * version of this flow registered right after the password step with a temporary placeholder
 * name/username, then PATCHed the real ones in afterward — dropped because it left a real,
 * queryable account sitting in the database before someone had ever even typed a username.)
 * [REGISTER_SHARING] is a placeholder step only — real design deferred, see FEATURE_IDEAS.md —
 * that simply finishes the flow once satisfied. [goBack] allows stepping back through all of
 * these, all the way to [REGISTER_EMAIL]; since no account exists until [submitUsername]
 * succeeds, there's no "already registered" edge case to worry about on the way back down. */
enum class AuthStep { WELCOME, LOGIN, REGISTER_EMAIL, REGISTER_PASSWORD, REGISTER_NAME, REGISTER_USERNAME, REGISTER_SHARING }

private const val MIN_PASSWORD_LENGTH = 8
private const val USERNAME_DEBOUNCE_MS = 400L

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

    // REGISTER_NAME
    var firstName by mutableStateOf("")
        private set
    var lastName by mutableStateOf("")
        private set

    // REGISTER_USERNAME — same debounced-check pattern as MyProfileViewModel's username editor
    // (see UsernameCheckState there), reused as-is rather than duplicated.
    var usernameDraft by mutableStateOf("")
        private set
    var usernameCheck by mutableStateOf<UsernameCheckState>(UsernameCheckState.Idle)
        private set
    private var usernameCheckJob: Job? = null

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
            AuthStep.REGISTER_SHARING -> AuthStep.REGISTER_USERNAME
            AuthStep.REGISTER_USERNAME -> AuthStep.REGISTER_NAME
            AuthStep.REGISTER_NAME -> AuthStep.REGISTER_PASSWORD
            AuthStep.REGISTER_PASSWORD -> AuthStep.REGISTER_EMAIL
            AuthStep.REGISTER_EMAIL, AuthStep.LOGIN -> AuthStep.WELCOME
            AuthStep.WELCOME -> step
        }
    }

    fun onContinueWithEmailClicked() = goTo(AuthStep.REGISTER_EMAIL)
    fun onSignInClicked() = goTo(AuthStep.LOGIN)
    /** Verifies the address isn't already registered before moving on, rather than only checking
     * that it looks like an email. Registration itself rejects duplicates (AuthService.register,
     * plus a unique constraint on the column), but that only fires at the very end — so without
     * this, someone would enter a password, a display name and a username, and only then be told
     * the email was taken all along, with no obvious way back to change it. */
    fun onEmailStepContinue() {
        if (!isEmailValid || isLoading) return
        errorMessage = null
        viewModelScope.launch {
            isLoading = true
            repository.checkEmailAvailability(email.trim()).fold(
                onSuccess = { result ->
                    isLoading = false
                    if (result.available) {
                        goTo(AuthStep.REGISTER_PASSWORD)
                    } else {
                        errorMessage = "That email already has an Emigo account."
                    }
                },
                // A check that couldn't reach the server must not become a wall in front of
                // sign-up: registration still enforces uniqueness for real, so letting the step
                // proceed offline is safe, just later-failing.
                onFailure = {
                    isLoading = false
                    goTo(AuthStep.REGISTER_PASSWORD)
                },
            )
        }
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
                onSuccess = {
                    // See submitRegister's identical comment on clearing this before the screen
                    // unmounts, to keep Google Password Manager's save-prompt heuristic quiet.
                    password = ""
                    onSuccess()
                },
                onFailure = { errorMessage = it.message ?: "Something went wrong" },
            )
            isLoading = false
        }
    }

    /** No network call — just validates and moves on. The account doesn't exist yet; see this
     * file's own top-of-file doc comment for where it actually gets created. */
    fun submitRegister() {
        if (!isEmailValid || !isPasswordValid) {
            errorMessage = "Please check your details"
            return
        }
        goTo(AuthStep.REGISTER_NAME)
    }

    fun onFirstNameChange(value: String) {
        firstName = value
        errorMessage = null
    }

    fun onLastNameChange(value: String) {
        lastName = value
        errorMessage = null
    }

    val isNameValid: Boolean
        get() = firstName.isNotBlank() && lastName.isNotBlank()

    /** Also just local validation, same as [submitRegister] — still nothing to save server-side
     * until a username is confirmed too. */
    fun submitName() {
        if (!isNameValid) {
            errorMessage = "Please enter your first and last name"
            return
        }
        goTo(AuthStep.REGISTER_USERNAME)
    }

    /** Mirrors MyProfileViewModel.onUsernameDraftChange's filtering/debounce exactly, but checks
     * through [AuthRepository.checkUsernameAvailability] (the public, pre-auth endpoint) rather
     * than UserRepository's authenticated one — no account exists yet at this point. */
    fun onUsernameDraftChange(value: String) {
        val filtered = value.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(30).lowercase()
        usernameDraft = filtered
        errorMessage = null
        usernameCheckJob?.cancel()

        if (filtered.length < 3) {
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

    fun pickUsernameSuggestion(name: String) = onUsernameDraftChange(name)

    /** The moment the account actually comes into existence — every field (email, password,
     * name, a confirmed-available username) is finally in hand, so this is the first and only
     * [AuthRepository.register] call in the whole flow. */
    fun submitUsername() {
        if (usernameDraft.length < 3) {
            errorMessage = "Username must be at least 3 characters"
            return
        }
        if (usernameCheck !is UsernameCheckState.Available) {
            errorMessage = "Pick an available username first"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val displayName = "${firstName.trim()} ${lastName.trim()}".trim()
            repository.register(email.trim(), password, displayName, usernameDraft).fold(
                onSuccess = {
                    // Google Password Manager's "Save password?" prompt fires off the password
                    // field being non-empty when it disappears from the view tree (i.e. when this
                    // screen unmounts) — clearing it first, before that happens, leaves nothing
                    // for the save-prompt heuristic to act on.
                    password = ""
                    goTo(AuthStep.REGISTER_SHARING)
                },
                onFailure = { errorMessage = it.message ?: "Something went wrong" },
            )
            isLoading = false
        }
    }
}
