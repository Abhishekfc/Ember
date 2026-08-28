package com.emigo.app.ui.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.AuthRepository
import com.emigo.app.data.SignInOutcome
import com.emigo.app.ui.profile.UsernameCheckState
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
 * The account itself now lives with Firebase Authentication, not this app's own backend — see
 * [AuthRepository]. [REGISTER_NAME]/[REGISTER_USERNAME] are reached two different ways: the
 * ordinary path (right after [REGISTER_EMAIL]/[REGISTER_PASSWORD], for a genuinely new sign-up),
 * and a *resumed* path — landing here with a Firebase identity that already exists but has no
 * Emigo profile yet, either because a previous sign-up was interrupted before finishing, or
 * because this is the first time this Google account has ever been used with Emigo (see
 * [needsFreshFirebaseAccount], and [submitUsername] for where the two paths converge back into
 * one backend call either way).
 */
enum class AuthStep { WELCOME, LOGIN, FORGOT_PASSWORD, REGISTER_EMAIL, REGISTER_PASSWORD, REGISTER_NAME, REGISTER_USERNAME, REGISTER_WIDGET, REGISTER_SHARING }

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

    /** True once the Emigo profile has actually been created (or found) server-side. The steps
     * after that point (widget, add-a-friend) are post-account, and the credentials that created
     * it are no longer valid to resubmit — see [submitUsername] and [goBack]. */
    private var accountCreated = false

    /** True for the ordinary sign-up path (create a brand-new Firebase identity in
     * [submitUsername]); false when [REGISTER_NAME]/[REGISTER_USERNAME] were reached instead via
     * an *already*-signed-in Firebase identity with no Emigo profile yet — a resumed sign-up
     * (see [submitLogin]) or a first-time Google sign-in (see [onGoogleSignInResult]) — in which
     * case [submitUsername] only needs to finish the Emigo side, never create another identity. */
    private var needsFreshFirebaseAccount = true

    var email by mutableStateOf("")
        private set

    /** The [LOGIN] step's own identifier field. Deliberately a real email only, unlike the old
     * backend-driven flow this replaced: Firebase Authentication signs in by email (or a linked
     * Google account), it has no concept of a username at all, so a username typed here can no
     * longer be resolved to an account the way the old custom backend login could. */
    var loginIdentifier by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // FORGOT_PASSWORD — deliberately its own email field and its own loading/result state, never
    // borrowed from LOGIN's loginIdentifier/isLoading. This screen is reached *from* a failed or
    // in-progress login attempt, so sharing state with it risks exactly the kind of cross-talk
    // this file's own onContinueWithEmailClicked/onSignInClicked already had to fix once for
    // password (see their doc comment) — easier to keep this screen fully self-contained than to
    // reason about every place shared state could leak between the two.
    var forgotPasswordEmail by mutableStateOf("")
        private set
    var isSendingPasswordReset by mutableStateOf(false)
        private set
    var passwordResetSent by mutableStateOf(false)
        private set

    val isForgotPasswordEmailValid: Boolean
        get() = Patterns.EMAIL_ADDRESS.matcher(forgotPasswordEmail.trim()).matches()

    fun onForgotPasswordEmailChange(value: String) {
        forgotPasswordEmail = value
        passwordResetSent = false
    }

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

    val isLoginEmailValid: Boolean
        get() = Patterns.EMAIL_ADDRESS.matcher(loginIdentifier.trim()).matches()

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

    /** [AuthStep.REGISTER_WIDGET] onward. Public because that step is the one place in this flow
     * that advances on a plain UI decision rather than on a network result or a validated field,
     * so there's nothing for the ViewModel itself to check first. */
    fun onWidgetStepDone() = goTo(AuthStep.REGISTER_SHARING)

    fun goBack() {
        isMovingForward = false
        errorMessage = null
        step = when (step) {
            // Both of these come *after* the account has succeeded, so it already exists.
            // Stepping back past the widget step would land on the very screens that created it,
            // where pressing continue would try to redo work that's already done. The widget step
            // is the floor for back navigation.
            AuthStep.REGISTER_SHARING -> AuthStep.REGISTER_WIDGET
            AuthStep.REGISTER_WIDGET -> AuthStep.REGISTER_WIDGET
            AuthStep.REGISTER_USERNAME -> AuthStep.REGISTER_NAME
            AuthStep.REGISTER_NAME -> AuthStep.REGISTER_PASSWORD
            AuthStep.REGISTER_PASSWORD -> AuthStep.REGISTER_EMAIL
            AuthStep.REGISTER_EMAIL, AuthStep.LOGIN -> AuthStep.WELCOME
            AuthStep.FORGOT_PASSWORD -> AuthStep.LOGIN
            AuthStep.WELCOME -> step
        }
        // Backing out to the fork between signing in and signing up abandons whichever attempt was
        // in progress, so the password typed for it shouldn't outlive it — the same reasoning as
        // onSignInClicked/onContinueWithEmailClicked, covering the case where the person leaves via
        // the back arrow rather than by picking the other option.
        if (step == AuthStep.WELCOME) password = ""
    }

    /**
     * Both entry points clear [password] first, because sign-in and sign-up share that one field
     * and it is otherwise only cleared on *success*.
     *
     * The path that exposed this: try to sign in, get "invalid email or password" because no
     * account exists, go back, start creating one instead — and the sign-up password step opens
     * already filled in with the password just typed for a different account. Easy to miss (it
     * renders as dots) and easy to accept, so the new account silently gets a password the person
     * never chose for it, and which they believe belongs to some other account entirely.
     *
     * Cleared here, at the fork between the two flows, rather than on every step change: going
     * back one step *within* sign-up to correct an email should keep the password already typed.
     */
    fun onContinueWithEmailClicked() {
        password = ""
        goTo(AuthStep.REGISTER_EMAIL)
    }

    fun onSignInClicked() {
        password = ""
        goTo(AuthStep.LOGIN)
    }

    /** Verifies the address isn't already registered before moving on, rather than only checking
     * that it looks like an email. Sign-up itself still rejects duplicates for real (Firebase's
     * own createUserWithEmailAndPassword), but that only fires at the very end — so without this,
     * someone would enter a password, a name and a username, and only then be told the email was
     * taken all along, with no obvious way back to change it. */
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
                // sign-up: the real sign-up call still enforces uniqueness for real, so letting
                // the step proceed offline is safe, just later-failing.
                onFailure = {
                    isLoading = false
                    goTo(AuthStep.REGISTER_PASSWORD)
                },
            )
        }
    }

    /** Fires once the actual Google Sign-In intent (launched by LoginScreen, which owns the
     * Activity context this needs) has returned an ID token — null on cancel/failure, in which
     * case this is a silent no-op rather than an error message for someone who simply backed out
     * of the account picker. */
    fun onGoogleSignInResult(googleIdToken: String?, onSuccess: () -> Unit) {
        if (googleIdToken == null || isLoading) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.signInWithGoogle(googleIdToken).fold(
                onSuccess = { outcome -> handleSignInOutcome(outcome, onSuccess) },
                onFailure = { errorMessage = it.message ?: "Google sign-in failed" },
            )
            isLoading = false
        }
    }

    fun submitLogin(onSuccess: () -> Unit) {
        // The button stays tappable while the request is in flight, so on a slow connection two
        // taps means two login calls — and two [onSuccess] callbacks, i.e. navigating onward
        // twice. Whichever request lost the race also gets to overwrite the outcome of the one
        // that won, so a successful sign-in could still end up showing an error.
        if (isLoading) return
        if (!isLoginEmailValid || password.isBlank()) {
            errorMessage = "Please fill in every field"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.signIn(loginIdentifier.trim(), password).fold(
                onSuccess = { outcome -> handleSignInOutcome(outcome, onSuccess) },
                onFailure = { errorMessage = it.message ?: "Something went wrong" },
            )
            isLoading = false
        }
    }

    /** Shared by [submitLogin] and [onGoogleSignInResult] — either can land on a Firebase
     * identity with no Emigo profile yet (see this file's own top-of-file doc comment), in which
     * case there's nothing to call [onSuccess] with; the flow routes into finishing the profile
     * instead of signing straight in. [onSuccess] is only relevant to the login path — a fresh
     * Google identity has nowhere to be "successfully signed in" to yet. */
    private fun handleSignInOutcome(outcome: SignInOutcome, onSuccess: (() -> Unit)? = null) {
        when (outcome) {
            is SignInOutcome.SignedIn -> {
                password = ""
                onSuccess?.invoke()
            }
            is SignInOutcome.NeedsProfile -> {
                needsFreshFirebaseAccount = false
                val parts = outcome.suggestedDisplayName.trim().split(" ", limit = 2)
                firstName = parts.getOrElse(0) { "" }
                lastName = parts.getOrElse(1) { "" }
                goTo(AuthStep.REGISTER_NAME)
            }
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

    // Last name is optional — displayName (below) already collapses a blank one down to just
    // the first name cleanly, so there's nothing server-side that actually needs it.
    val isNameValid: Boolean
        get() = firstName.isNotBlank()

    /** Also just local validation, same as [submitRegister] — still nothing to save server-side
     * until a username is confirmed too. */
    fun submitName() {
        if (!isNameValid) {
            errorMessage = "Please enter your first name"
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

    /** The moment the Emigo profile actually comes into existence. Branches on
     * [needsFreshFirebaseAccount]: the ordinary sign-up path still needs Firebase itself to create
     * the identity first ([AuthRepository.signUp]); the resumed/Google path already has one
     * signed in, so this only needs the backend half ([AuthRepository.completeProfile]) — see
     * this file's own top-of-file doc comment for the two ways of arriving here. */
    fun submitUsername() {
        // Belt and braces alongside goBack's own floor: this must run exactly once per account.
        // Any second call can only ever fail (the identity/email is already taken by the account
        // this same flow just made), so it's an error state with no useful outcome — moving on is
        // strictly better than reporting it.
        if (accountCreated) {
            goTo(AuthStep.REGISTER_WIDGET)
            return
        }
        // A slow network makes the button tappable for as long as the request is in flight; two
        // taps means two calls, the second of which fails as a duplicate and replaces the success
        // with an error message for an account that was in fact created.
        if (isLoading) return
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
            val result = if (needsFreshFirebaseAccount) {
                repository.signUp(email.trim(), password, displayName, usernameDraft)
            } else {
                repository.completeProfile(displayName, usernameDraft)
            }
            result.fold(
                onSuccess = {
                    accountCreated = true
                    // Google Password Manager's "Save password?" prompt fires off the password
                    // field being non-empty when it disappears from the view tree (i.e. when this
                    // screen unmounts) — clearing it first, before that happens, leaves nothing
                    // for the save-prompt heuristic to act on.
                    password = ""
                    // The widget is what this app actually is, so it's explained before anyone
                    // is asked to invite friends to it — the invite reads as worth sending once
                    // you know what the other person is being invited to.
                    goTo(AuthStep.REGISTER_WIDGET)
                },
                onFailure = { errorMessage = it.message ?: "Something went wrong" },
            )
            isLoading = false
        }
    }

    /** Opens the dedicated FORGOT_PASSWORD screen — see [forgotPasswordEmail]'s own doc comment
     * for why that screen owns entirely separate state rather than reusing anything from LOGIN.
     * Pre-filling with whatever's already typed here is purely a convenience for the common case
     * (already typed an email, then remembered you forgot the password); the new screen's field
     * is independently editable from that point on, and this never touches [loginIdentifier]. */
    fun onForgotPasswordClicked() {
        forgotPasswordEmail = loginIdentifier
        passwordResetSent = false
        goTo(AuthStep.FORGOT_PASSWORD)
    }

    /** Firebase sends the email and hosts the reset page itself — this just triggers it and shows
     * a confirmation, without revealing whether the address actually has an account (the
     * confirmation reads the same either way, matching how real password-reset flows avoid
     * turning "forgot password" into an email-enumeration oracle). */
    fun sendPasswordReset() {
        if (!isForgotPasswordEmailValid || isSendingPasswordReset) return
        viewModelScope.launch {
            isSendingPasswordReset = true
            repository.sendPasswordReset(forgotPasswordEmail.trim())
            passwordResetSent = true
            isSendingPasswordReset = false
        }
    }
}
