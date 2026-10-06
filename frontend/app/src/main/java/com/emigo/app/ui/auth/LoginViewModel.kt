package com.emigo.app.ui.auth

import com.emigo.app.R
import com.emigo.app.core.StringProvider

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.repository.AuthRepository
import com.emigo.app.data.repository.EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS
import com.emigo.app.data.repository.SignInOutcome
import com.emigo.app.data.firebaseErrorMessage
import com.emigo.app.data.repository.needsEmailVerification
import com.emigo.app.data.repository.verificationDeadlineFor
import com.emigo.app.ui.profile.UsernameCheckState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Every screen the auth flow can be on. [WELCOME] is the entry point. The new-account path
 * ([REGISTER_EMAIL] > [REGISTER_PASSWORD] > [REGISTER_NAME] > [REGISTER_USERNAME] >
 * [REGISTER_SHARING]) is one question per screen, since it's a new user's first impression.
 * [LOGIN] is a single email+password screen: a returning user knows both, so splitting it would
 * only add a tap.
 *
 * The account lives with Firebase Authentication, not this app's backend (see [AuthRepository]).
 * [REGISTER_NAME] and [REGISTER_USERNAME] are reached one way only, forward from [REGISTER_EMAIL]
 * and [REGISTER_PASSWORD] on a new sign-up. Signing in never routes here: a Firebase identity with
 * no Emigo profile reports "no account found" instead of continuing into sign-up, so the two flows
 * can't be confused.
 */
enum class AuthStep { WELCOME, LOGIN, FORGOT_PASSWORD, REGISTER_EMAIL, REGISTER_PASSWORD, REGISTER_NAME, REGISTER_USERNAME, NEEDS_EMAIL_VERIFICATION, REGISTER_WIDGET, REGISTER_SHARING }

private const val MIN_PASSWORD_LENGTH = 8
private const val USERNAME_DEBOUNCE_MS = 400L

/**
 * [initialPendingVerificationEmail] and [initialPendingVerificationDeadlineMillis] seed [step] onto
 * [AuthStep.NEEDS_EMAIL_VERIFICATION] before the first composition, from TokenStore's synchronously
 * read local echo (see MainActivity's onCreate). That's why they're constructor params instead of
 * a later call to [showVerificationRequired]: waiting on that async check produced the original
 * flash into the full app shell before bouncing back to this screen. Null otherwise (a fresh
 * WELCOME start, or a returning session with nothing pending).
 */
class LoginViewModel(
    private val strings: StringProvider,
    private val repository: AuthRepository,
    initialPendingVerificationEmail: String? = null,
    initialPendingVerificationDeadlineMillis: Long? = null,
) : ViewModel() {

    var step by mutableStateOf(
        if (initialPendingVerificationEmail != null) AuthStep.NEEDS_EMAIL_VERIFICATION else AuthStep.WELCOME,
    )
        private set

    /** Which way the step transition slides: forward slides the new step in from the right, back
     * reverses it, so motion matches the flow's direction. */
    var isMovingForward by mutableStateOf(true)
        private set

    /** True once the Emigo profile has been created (or found) server-side. The later steps
     * (widget, add a friend) are post-account, and the credentials that created it are no longer
     * valid to resubmit (see [submitUsername] and [goBack]). */
    private var accountCreated = false

    var email by mutableStateOf("")
        private set

    /** The [LOGIN] step's identifier field. Firebase signs in by email only, but
     * AuthRepository.signIn resolves a username typed here to its email through a backend lookup
     * first, so this field accepts either, as the old custom backend login did. */
    var loginIdentifier by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set

    /** The address [AuthStep.NEEDS_EMAIL_VERIFICATION] shows: from [submitUsername]'s result on a
     * new sign-up, or [SignInOutcome.NeedsVerification] on a returning sign-in. Always the
     * backend-confirmed email of the account being verified, never just whatever was last typed. */
    var pendingVerificationEmail by mutableStateOf(initialPendingVerificationEmail ?: "")
        private set

    /** The deadline EmailVerificationExpiryService enforces server-side (epoch millis).
     * VerifyEmailStep counts down to this, not a fresh timer, so leaving and reopening the screen
     * (or app) can't reset it. */
    var pendingVerificationDeadlineMillis by mutableStateOf(initialPendingVerificationDeadlineMillis ?: 0L)
        private set
    var isResendingVerification by mutableStateOf(false)
        private set
    var verificationResendMessage by mutableStateOf<String?>(null)
        private set
    var isCheckingVerification by mutableStateOf(false)
        private set
    var verificationCheckError by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // FORGOT_PASSWORD has its own email field and loading/result state, never borrowed from LOGIN's.
    // This screen is reached from a failed or in-progress login, and shared state risked the
    // cross-talk onContinueWithEmailClicked/onSignInClicked already had to fix for the password (see
    // their doc).
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

    // REGISTER_USERNAME: the same debounced-check pattern as MyProfileViewModel's username editor
    // (see UsernameCheckState), reused instead of duplicated.
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

    /** [AuthStep.REGISTER_WIDGET] onward. Public because that step advances on a plain UI decision,
     * not a network result or validated field, so there's nothing to check first. */
    fun onWidgetStepDone() = goTo(AuthStep.REGISTER_SHARING)

    fun goBack() {
        isMovingForward = false
        errorMessage = null
        step = when (step) {
            // Both come after the account succeeded, so it already exists. Stepping back past the
            // widget step would land on the screens that created it, where continue would redo
            // finished work. The widget step is the floor for back navigation.
            AuthStep.REGISTER_SHARING -> AuthStep.REGISTER_WIDGET
            AuthStep.REGISTER_WIDGET -> AuthStep.REGISTER_WIDGET
            // No back button on this step (see VerifyEmailStep; sign out is the only way off), but
            // this `when` must stay exhaustive over every AuthStep regardless.
            AuthStep.NEEDS_EMAIL_VERIFICATION -> AuthStep.NEEDS_EMAIL_VERIFICATION
            AuthStep.REGISTER_USERNAME -> AuthStep.REGISTER_NAME
            AuthStep.REGISTER_NAME -> AuthStep.REGISTER_PASSWORD
            AuthStep.REGISTER_PASSWORD -> AuthStep.REGISTER_EMAIL
            AuthStep.REGISTER_EMAIL, AuthStep.LOGIN -> AuthStep.WELCOME
            AuthStep.FORGOT_PASSWORD -> AuthStep.LOGIN
            AuthStep.WELCOME -> step
        }
        // Backing out to the sign-in/sign-up fork abandons whichever attempt was in progress, so its
        // password shouldn't outlive it. Same reasoning as onSignInClicked/onContinueWithEmailClicked,
        // for leaving via the back arrow.
        if (step == AuthStep.WELCOME) password = ""
    }

    /**
     * Both entry points clear [password] first, because sign-in and sign-up share that field and
     * it's otherwise cleared only on success.
     *
     * What exposed it: try to sign in, get "invalid email or password" because no account exists,
     * go back, start creating one, and the sign-up password step opens already filled with the
     * password typed for a different account. It renders as dots, so it's easy to miss and accept,
     * giving the new account a password the person never chose for it.
     *
     * Cleared at the fork between the flows, not on every step change: going back one step within
     * sign-up to fix an email should keep the password.
     */
    fun onContinueWithEmailClicked() {
        password = ""
        goTo(AuthStep.REGISTER_EMAIL)
    }

    fun onSignInClicked() {
        password = ""
        goTo(AuthStep.LOGIN)
    }

    /** Checks the address isn't already registered before moving on, not just that it looks like an
     * email. Sign-up still rejects duplicates for real (Firebase's createUserWithEmailAndPassword),
     * but only at the very end; without this someone would enter a password, name and username
     * before learning the email was taken, with no obvious way back. */
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
                        errorMessage = strings.get(R.string.login_error_email_taken)
                    }
                },
                // A check that couldn't reach the server mustn't become a wall in front of sign-up:
                // the real sign-up still enforces uniqueness, so proceeding offline is safe, just
                // later-failing.
                onFailure = {
                    isLoading = false
                    goTo(AuthStep.REGISTER_PASSWORD)
                },
            )
        }
    }

    fun submitLogin(onSuccess: () -> Unit) {
        // The button stays tappable during the request, so on a slow connection two taps meant two
        // login calls and two [onSuccess] callbacks (navigating onward twice), and the losing request
        // could overwrite the winner's outcome so a successful sign-in showed an error.
        if (isLoading) return
        if (loginIdentifier.isBlank() || password.isBlank()) {
            errorMessage = strings.get(R.string.login_error_fill_all)
            return
        }
        // Separate from the blank case, and only for something that looks like an attempted email:
        // AuthRepository.signIn accepts a username too (it resolves it to an email, since Firebase
        // has no usernames), so email-shaped input can't be required of everyone. It still catches a
        // malformed email ("abc@") with its own message instead of a network call that can only
        // fail. This used to fire for anything that wasn't a valid email, including a genuine
        // username, showing "Please fill in every field" to someone who had.
        if (loginIdentifier.contains("@") && !isLoginEmailValid) {
            errorMessage = strings.get(R.string.login_error_invalid_email)
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.signIn(loginIdentifier.trim(), password).fold(
                onSuccess = { outcome -> handleSignInOutcome(outcome, onSuccess) },
                onFailure = { errorMessage = it.message ?: strings.get(R.string.error_something_went_wrong) },
            )
            isLoading = false
        }
    }

    /** [submitLogin] can land on a Firebase identity with no Emigo profile (a sign-up interrupted
     * before finishing). Then there's nothing to call [onSuccess] with; it shows the same error as a
     * wrong password instead (see the NeedsProfile branch). */
    private fun handleSignInOutcome(outcome: SignInOutcome, onSuccess: (() -> Unit)? = null) {
        when (outcome) {
            is SignInOutcome.SignedIn -> {
                password = ""
                onSuccess?.invoke()
            }
            is SignInOutcome.NeedsProfile -> {
                // Signing in either gets you into your account or says it couldn't; it must never turn
                // into the sign-up flow, which routing this to the name/username steps used to do
                // (indistinguishable from the app confusing the two).
                //
                // Here Firebase accepted the credentials but this backend has no profile for that
                // identity. Not spelled out to the user: "this email exists but has no profile"
                // would confirm to anyone guessing that an address is registered. Same wording as a
                // wrong password for that reason.
                //
                // Signed out again so no half-authenticated session is left for the next screen to
                // trip over. The Firebase identity is left alone: a profile-less identity isn't safe
                // to assume worthless and delete, because a debug build pointed at the local backend
                // sees every real production account this way.
                FirebaseAuth.getInstance().signOut()
                password = ""
                // The exact string firebaseErrorMessage returns for a wrong password, so the two
                // cases are indistinguishable and nobody guessing can use the difference to find
                // registered addresses.
                errorMessage = strings.get(R.string.login_error_bad_credentials)
            }
            is SignInOutcome.NeedsVerification -> {
                // accountCreated stays false here (unlike the new sign-up path in submitUsername):
                // this account already existed, it's just unverified, which is what makes
                // onEmailVerifiedContinue call onAuthenticated directly instead of continuing into
                // the widget/sharing onboarding.
                password = ""
                pendingVerificationEmail = outcome.email
                pendingVerificationDeadlineMillis = outcome.verifyByEpochMillis
                goTo(AuthStep.NEEDS_EMAIL_VERIFICATION)
            }
        }
    }

    /** No network call, just validation. The account doesn't exist yet; see [submitUsername] for
     * where it's created. */
    fun submitRegister() {
        if (!isEmailValid || !isPasswordValid) {
            errorMessage = strings.get(R.string.login_error_check_details)
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

    // Last name is optional; displayName (below) collapses a blank one to just the first name, so
    // nothing server-side needs it.
    val isNameValid: Boolean
        get() = firstName.isNotBlank()

    /** Also just local validation, like [submitRegister]; nothing to save server-side until a
     * username is confirmed too. */
    fun submitName() {
        if (!isNameValid) {
            errorMessage = strings.get(R.string.login_error_first_name)
            return
        }
        goTo(AuthStep.REGISTER_USERNAME)
    }

    /** Mirrors MyProfileViewModel.onUsernameDraftChange's filtering and debounce, but checks through
     * [AuthRepository.checkUsernameAvailability] (the public, pre-auth endpoint) instead of
     * UserRepository's authenticated one, since no account exists yet. */
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

    /** The moment the Emigo profile comes into existence: always the full [AuthRepository.signUp]
     * (Firebase identity first, then the backend profile on top). A second path for a resumed
     * sign-up (an already-signed-in identity with no profile, reached by signing in), which called
     * [AuthRepository.completeProfile] alone, is gone, because signing in now reports "no account
     * found" instead of continuing into sign-up. signUp still reuses an existing signed-in identity
     * when it's the same address, which covers a retry after the backend half failed. */
    fun submitUsername() {
        // Belt and braces with goBack's floor: this must run once per account. A second call can only
        // fail (the identity/email is taken by the account this flow just made), an error with no
        // useful outcome, so moving on is better than reporting it.
        if (accountCreated) {
            goTo(AuthStep.REGISTER_WIDGET)
            return
        }
        // A slow network keeps the button tappable while the request is in flight; two taps meant two
        // calls, the second failing as a duplicate and replacing the success with an error for an
        // account that was in fact created.
        if (isLoading) return
        if (usernameDraft.length < 3) {
            errorMessage = strings.get(R.string.error_username_too_short)
            return
        }
        if (usernameCheck !is UsernameCheckState.Available) {
            errorMessage = strings.get(R.string.error_username_pick_available)
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val displayName = "${firstName.trim()} ${lastName.trim()}".trim()
            val result = repository.signUp(email.trim(), password, displayName, usernameDraft)
            result.fold(
                onSuccess = { profile ->
                    accountCreated = true
                    // Google Password Manager's "Save password?" prompt fires when a non-empty password
                    // field leaves the view tree (this screen unmounting). Clearing it first leaves
                    // nothing for that heuristic to act on.
                    password = ""
                    if (needsEmailVerification(profile)) {
                        // Block here, before the widget/sharing steps: showing "come look at the
                        // widget" onboarding and only then revealing you're blocked would read as
                        // bait-and-switch. Compulsory means compulsory from the moment the account
                        // exists.
                        pendingVerificationEmail = profile.email
                        pendingVerificationDeadlineMillis = verificationDeadlineFor(profile)
                        repository.rememberPendingVerification(pendingVerificationEmail, pendingVerificationDeadlineMillis)
                        goTo(AuthStep.NEEDS_EMAIL_VERIFICATION)
                    } else {
                        // The widget is what this app is, so it's explained before anyone is asked to
                        // invite friends to it; the invite reads as worth sending once you know what
                        // the other person is being invited to.
                        goTo(AuthStep.REGISTER_WIDGET)
                    }
                },
                onFailure = { errorMessage = it.message ?: strings.get(R.string.error_something_went_wrong) },
            )
            isLoading = false
        }
    }

    /** Routes a session resumed at cold start (see AuthRepository.resumeSession; EmberRoot is the
     * one caller) onto the verification screen. Unlike the passive 403 listener it replaced, this
     * comes only from a single authoritative check per launch against a freshly refreshed token,
     * never a stale token some background request carried, so it can't put an already-verified user
     * back here, let alone repeatedly.
     *
     * accountCreated stays false, like [SignInOutcome.NeedsVerification] arriving through sign-in:
     * the account already exists, so verifying re-enters the app directly instead of restarting the
     * widget/sharing onboarding. */
    fun showVerificationRequired(outcome: SignInOutcome.NeedsVerification) {
        pendingVerificationEmail = outcome.email
        pendingVerificationDeadlineMillis = outcome.verifyByEpochMillis
        goTo(AuthStep.NEEDS_EMAIL_VERIFICATION)
    }

    /** Re-sends the verification link Firebase sent at sign-up, always to [pendingVerificationEmail].
     * Verified directly against Firebase that two sends in quick succession (the shape of tapping
     * this right after the automatic send at sign-up) are rejected with a rate-limit error, not a
     * network one, so this uses the shared [firebaseErrorMessage] mapping instead of a hardcoded
     * "check your connection" that would be wrong for that likely-common case. */
    fun resendVerificationEmail() {
        if (isResendingVerification) return
        viewModelScope.launch {
            isResendingVerification = true
            verificationResendMessage = null
            runCatching { FirebaseAuth.getInstance().currentUser?.sendEmailVerification()?.await() }
                .onSuccess { verificationResendMessage = strings.get(R.string.verify_resent) }
                .onFailure { verificationResendMessage = firebaseErrorMessage(it) ?: strings.get(R.string.verify_resend_failed) }
            isResendingVerification = false
        }
    }

    /** Firebase's local `isEmailVerified` is a snapshot from when the ID token was last issued, and
     * clicking the email link doesn't push anything to a running app, so this explicitly refreshes
     * before re-checking.
     *
     * `reload()` alone isn't enough: it updates `isEmailVerified` on this [FirebaseUser] but not the
     * cached ID token every backend call attaches (see NetworkModule's authInterceptor), which still
     * carries the old `email_verified: false` claim. Without forcing a fresh token, the next
     * authenticated call (Home's feed fetch, moments after landing in the app) would send the stale
     * token, be rejected by the same backend gate this screen just passed, and bounce back here: the
     * loop this line prevents.
     *
     * [onAuthenticated] is called only for a returning sign-in (accountCreated false, see
     * [SignInOutcome.NeedsVerification]); a fresh sign-up still has the widget/sharing onboarding
     * ahead.
     *
     * It also asks the backend (via [AuthRepository.resumeSession]), not just Firebase's local flag:
     * this button is reachable only before the countdown hits zero (VerifyEmailStep disables it once
     * expired), but that guard reads the device's own clock. The backend's answer decides whether a
     * verification counted (see FirebaseAuthenticationFilter's deadline check); trusting Firebase
     * alone would let a slightly fast clock through a request the deadline refuses everywhere else. */
    fun onEmailVerifiedContinue(onAuthenticated: () -> Unit) {
        if (isCheckingVerification) return
        viewModelScope.launch {
            isCheckingVerification = true
            verificationCheckError = null
            val user = FirebaseAuth.getInstance().currentUser
            runCatching { user?.reload()?.await() }
            if (user?.isEmailVerified == true) {
                runCatching { user.getIdToken(true).await() }
                val outcome = repository.resumeSession().getOrNull()
                if (outcome is SignInOutcome.SignedIn) {
                    repository.forgetPendingVerification()
                    isCheckingVerification = false
                    if (accountCreated) goTo(AuthStep.REGISTER_WIDGET) else onAuthenticated()
                } else if (outcome is SignInOutcome.NeedsVerification) {
                    // The backend disagrees: the verification hasn't landed there yet (a rare timing gap
                    // right after clicking the link) or arrived too late to count. Re-sync to its
                    // deadline instead of leaving this countdown at what it was showing.
                    pendingVerificationEmail = outcome.email
                    pendingVerificationDeadlineMillis = outcome.verifyByEpochMillis
                    isCheckingVerification = false
                    verificationCheckError = strings.get(R.string.verify_still_unverified)
                } else {
                    // A network failure, or NeedsProfile (the account no longer exists;
                    // EmailVerificationExpiryService deleted it). Neither is "try again in a second",
                    // so this reuses the same message instead of claiming to know which happened.
                    isCheckingVerification = false
                    verificationCheckError = strings.get(R.string.verify_still_unverified)
                }
            } else {
                isCheckingVerification = false
                verificationCheckError = strings.get(R.string.verify_still_unverified)
            }
        }
    }

    /** The escape hatch for what this screen exists to prevent: someone who typed an email they can't
     * access. The real sign-out (clearing the Firebase session, unregistering this device, wiping
     * cached account data) is EmberRoot's onSignOut, passed in from LoginScreen; this only resets
     * this ViewModel's state so the login screen it lands on starts fresh, not mid-flow for an
     * account that no longer exists in this session. */
    fun resetAfterSignOut(onSignOut: () -> Unit, welcomeMessage: String? = null) {
        onSignOut()
        accountCreated = false
        email = ""
        loginIdentifier = ""
        password = ""
        pendingVerificationEmail = ""
        pendingVerificationDeadlineMillis = 0L
        verificationResendMessage = null
        verificationCheckError = null
        // Every in-flight flag, not just the fields: each gates its own button (`enabled =
        // !isLoading` and friends), so one left stuck true by a request cut short by the sign-out
        // would leave that button permanently dead on a screen that looks normal.
        isLoading = false
        isCheckingVerification = false
        isResendingVerification = false
        errorMessage = welcomeMessage
        step = AuthStep.WELCOME
    }

    /** Opens the FORGOT_PASSWORD screen (see [forgotPasswordEmail] for why it owns separate state
     * instead of reusing LOGIN's). Pre-filling with what's already typed is a convenience for the
     * common case (typed an email, then remembered the password is forgotten); the new field is
     * editable independently from then on, and this never touches [loginIdentifier]. */
    fun onForgotPasswordClicked() {
        forgotPasswordEmail = loginIdentifier
        passwordResetSent = false
        goTo(AuthStep.FORGOT_PASSWORD)
    }

    /** Firebase sends the email and hosts the reset page; this triggers it and shows a confirmation
     * without revealing whether the address has an account (the confirmation reads the same either
     * way, so "forgot password" isn't an email-enumeration oracle). */
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
