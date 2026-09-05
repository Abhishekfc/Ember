package com.emigo.app.ui.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.AuthRepository
import com.emigo.app.data.EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS
import com.emigo.app.data.SignInOutcome
import com.emigo.app.data.firebaseErrorMessage
import com.emigo.app.data.needsEmailVerification
import com.emigo.app.data.verificationDeadlineFor
import com.emigo.app.ui.profile.UsernameCheckState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Every screen the auth flow can be on. [WELCOME] is the true entry point. The new-account path
 * ([REGISTER_EMAIL] then [REGISTER_PASSWORD] then [REGISTER_NAME] then [REGISTER_USERNAME] then
 * [REGISTER_SHARING]) is deliberately one question per screen — this is a brand-new user's very
 * first impression of the app, so it gets the unhurried, focused treatment. [LOGIN] stays a
 * single combined email+password screen: a returning user already knows both, so splitting it
 * the same way would just be an extra tap for no benefit.
 *
 * The account itself now lives with Firebase Authentication, not this app's own backend — see
 * [AuthRepository]. [REGISTER_NAME]/[REGISTER_USERNAME] are reached one way only: forward from
 * [REGISTER_EMAIL]/[REGISTER_PASSWORD] on a genuinely new sign-up. Signing in never routes here —
 * a Firebase identity with no Emigo profile behind it reports "no account found" instead of
 * quietly continuing into sign-up, so the two flows can't be mistaken for each other.
 */
enum class AuthStep { WELCOME, LOGIN, FORGOT_PASSWORD, REGISTER_EMAIL, REGISTER_PASSWORD, REGISTER_NAME, REGISTER_USERNAME, NEEDS_EMAIL_VERIFICATION, REGISTER_WIDGET, REGISTER_SHARING }

private const val MIN_PASSWORD_LENGTH = 8
private const val USERNAME_DEBOUNCE_MS = 400L

/**
 * [initialPendingVerificationEmail]/[initialPendingVerificationDeadlineMillis] seed [step] straight
 * onto [AuthStep.NEEDS_EMAIL_VERIFICATION] before this ViewModel's very first composition, from
 * TokenStore's synchronously-read local echo (see MainActivity's onCreate) — the whole reason this
 * takes constructor params here rather than a plain no-arg init and a later call to
 * [showVerificationRequired]: the async check that call would otherwise wait on is exactly what
 * produced the original flash into the full app shell before bouncing back out to this screen.
 * Null for every other case (a fresh WELCOME start, or a returning session with nothing pending).
 */
class LoginViewModel(
    private val repository: AuthRepository,
    initialPendingVerificationEmail: String? = null,
    initialPendingVerificationDeadlineMillis: Long? = null,
) : ViewModel() {

    var step by mutableStateOf(
        if (initialPendingVerificationEmail != null) AuthStep.NEEDS_EMAIL_VERIFICATION else AuthStep.WELCOME,
    )
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

    var email by mutableStateOf("")
        private set

    /** The [LOGIN] step's own identifier field. Firebase Authentication itself signs in by email
     * only, with no concept of a username at all — but AuthRepository.signIn resolves a username
     * typed here back to its email via a small backend lookup before ever reaching Firebase, so
     * this field accepts either, same as the old custom backend login did. */
    var loginIdentifier by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set

    /** Whichever address [AuthStep.NEEDS_EMAIL_VERIFICATION] is currently showing — set from
     * [submitUsername]'s own result on a fresh sign-up, or from [SignInOutcome.NeedsVerification]
     * on a returning sign-in; either way this is always the real backend-confirmed email for the
     * account actually being verified, never just whatever was last typed into a field. */
    var pendingVerificationEmail by mutableStateOf(initialPendingVerificationEmail ?: "")
        private set

    /** The same deadline EmailVerificationExpiryService enforces server-side (epoch millis) —
     * VerifyEmailStep counts down to this, not a fresh independently-started timer, so leaving and
     * reopening this screen (or the app itself) can never reset it. */
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
            // No back button shown on this step at all (see VerifyEmailStep) — "Sign out" is the
            // only way off it — but goBack's own when must stay exhaustive over every AuthStep
            // regardless of which ones the UI actually exposes a back arrow for.
            AuthStep.NEEDS_EMAIL_VERIFICATION -> AuthStep.NEEDS_EMAIL_VERIFICATION
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

    fun submitLogin(onSuccess: () -> Unit) {
        // The button stays tappable while the request is in flight, so on a slow connection two
        // taps means two login calls — and two [onSuccess] callbacks, i.e. navigating onward
        // twice. Whichever request lost the race also gets to overwrite the outcome of the one
        // that won, so a successful sign-in could still end up showing an error.
        if (isLoading) return
        if (loginIdentifier.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in every field"
            return
        }
        // A separate check from the blank case above, and only for something that actually looks
        // like an attempted email — AuthRepository.signIn accepts a username just as well (it
        // resolves it back to an email itself, since Firebase has no concept of one), so this
        // can't require email-shaped input from everyone the way it used to. It's still worth
        // catching a malformed email specifically ("abc@") with its own message rather than
        // letting it fall through to a network call that can only ever fail. Previously this
        // fired for *anything* that wasn't a valid email, including a genuine username — showing
        // "Please fill in every field" for someone who'd filled in both fields correctly.
        if (loginIdentifier.contains("@") && !isLoginEmailValid) {
            errorMessage = "Enter a valid email address"
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

    /** [submitLogin] can land on a Firebase identity with no Emigo profile yet — a previous
     * sign-up interrupted before finishing (see this file's own top-of-file doc comment) — in
     * which case there's nothing to call [onSuccess] with; the flow routes into finishing the
     * profile instead of signing straight in. */
    private fun handleSignInOutcome(outcome: SignInOutcome, onSuccess: (() -> Unit)? = null) {
        when (outcome) {
            is SignInOutcome.SignedIn -> {
                password = ""
                onSuccess?.invoke()
            }
            is SignInOutcome.NeedsProfile -> {
                // Signing in is signing in: it either gets you into your account or it tells you
                // it couldn't. It must never quietly turn into the sign-up flow, which is what
                // routing this to the name/username steps used to do — indistinguishable, from
                // the outside, from the app confusing the two.
                //
                // Reaching here means Firebase accepted the credentials but this backend has no
                // profile attached to that identity. Deliberately *not* spelled out to the user:
                // saying "this email exists but has no profile" would confirm to anyone typing
                // guesses that an address is registered here. Same wording as a wrong password
                // for that reason.
                //
                // Signed out again so no half-authenticated session is left behind for the next
                // screen to trip over. The Firebase identity itself is left alone — it is *not*
                // safe to assume a profile-less identity is worthless and delete it, because a
                // debug build pointed at the local backend sees every real production account
                // exactly this way.
                FirebaseAuth.getInstance().signOut()
                password = ""
                // Deliberately the exact string firebaseErrorMessage already returns for a wrong
                // password, not a distinct one: identical wording is what makes the two cases
                // indistinguishable, so nobody typing guesses can use the difference to work out
                // which addresses are registered.
                errorMessage = "Incorrect email or password"
            }
            is SignInOutcome.NeedsVerification -> {
                // accountCreated stays false here (unlike the fresh sign-up path in
                // submitUsername) — this account already existed, it's just not verified yet —
                // which is exactly what tells onEmailVerifiedContinue to call onAuthenticated
                // directly instead of continuing into the widget/sharing onboarding steps.
                password = ""
                pendingVerificationEmail = outcome.email
                pendingVerificationDeadlineMillis = outcome.verifyByEpochMillis
                goTo(AuthStep.NEEDS_EMAIL_VERIFICATION)
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

    /** The moment the Emigo profile actually comes into existence. Always the full
     * [AuthRepository.signUp] — Firebase identity first, then the backend profile on top of it.
     * There used to be a second path here for a *resumed* sign-up (an already-signed-in identity
     * with no profile yet, reached by signing in), which called [AuthRepository.completeProfile]
     * alone; that path is gone, because signing in now reports "no account found" rather than
     * quietly continuing into sign-up. signUp itself still reuses an existing signed-in identity
     * when it's genuinely the same address, which is what covers a retry after the backend half
     * failed. */
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
            val result = repository.signUp(email.trim(), password, displayName, usernameDraft)
            result.fold(
                onSuccess = { profile ->
                    accountCreated = true
                    // Google Password Manager's "Save password?" prompt fires off the password
                    // field being non-empty when it disappears from the view tree (i.e. when this
                    // screen unmounts) — clearing it first, before that happens, leaves nothing
                    // for the save-prompt heuristic to act on.
                    password = ""
                    if (needsEmailVerification(profile)) {
                        // Blocking here, before the widget/sharing steps, rather than after them —
                        // showing "come look at the widget" onboarding and only *then* revealing
                        // you're actually blocked would read as a bait-and-switch. Compulsory means
                        // compulsory from the moment the account exists.
                        pendingVerificationEmail = profile.email
                        pendingVerificationDeadlineMillis = verificationDeadlineFor(profile)
                        repository.rememberPendingVerification(pendingVerificationEmail, pendingVerificationDeadlineMillis)
                        goTo(AuthStep.NEEDS_EMAIL_VERIFICATION)
                    } else {
                        // The widget is what this app actually is, so it's explained before anyone
                        // is asked to invite friends to it — the invite reads as worth sending once
                        // you know what the other person is being invited to.
                        goTo(AuthStep.REGISTER_WIDGET)
                    }
                },
                onFailure = { errorMessage = it.message ?: "Something went wrong" },
            )
            isLoading = false
        }
    }

    /** Routes a session resumed at cold start (see AuthRepository.resumeSession, and MainActivity
     * for the one place that calls it) onto the verification screen. Unlike the passive 403
     * listener this replaced, this is only ever reached from a single authoritative check made
     * once per launch against a freshly refreshed token — never from whatever stale token some
     * background request happened to carry — so it can't put someone who has already verified
     * back onto this screen, let alone repeatedly.
     *
     * accountCreated stays false, same as [SignInOutcome.NeedsVerification] arriving through
     * sign-in: this account already exists, so verifying from here re-enters the app directly
     * rather than restarting the widget/sharing onboarding. */
    fun showVerificationRequired(outcome: SignInOutcome.NeedsVerification) {
        pendingVerificationEmail = outcome.email
        pendingVerificationDeadlineMillis = outcome.verifyByEpochMillis
        goTo(AuthStep.NEEDS_EMAIL_VERIFICATION)
    }

    /** Re-sends the same verification link Firebase already sent once at sign-up — the address
     * itself never changes here, this only ever resends to [pendingVerificationEmail]. Verified
     * directly against Firebase (not just read from this SDK call's own docs) that sending twice
     * in quick succession — the exact shape of tapping this button right after the automatic send
     * at sign-up — gets rejected with a rate-limit error, not a network one, which is why this
     * uses the same [firebaseErrorMessage] mapping every other Firebase Auth call in this app
     * already relies on instead of a single hardcoded "check your connection" guess that would be
     * wrong for that specific, likely-common case. */
    fun resendVerificationEmail() {
        if (isResendingVerification) return
        viewModelScope.launch {
            isResendingVerification = true
            verificationResendMessage = null
            runCatching { FirebaseAuth.getInstance().currentUser?.sendEmailVerification()?.await() }
                .onSuccess { verificationResendMessage = "Verification email sent." }
                .onFailure { verificationResendMessage = firebaseErrorMessage(it) ?: "Couldn't send that. Please try again." }
            isResendingVerification = false
        }
    }

    /** Firebase's local record of `isEmailVerified` is a snapshot from whenever this identity's ID
     * token was last issued — clicking the link in the email doesn't push anything back to an
     * already-running app, so this has to explicitly ask Firebase to refresh before re-checking,
     * or a genuinely-just-verified account would still read as unverified.
     *
     * `reload()` alone isn't enough, even though it does correctly update `isEmailVerified` on
     * this [FirebaseUser] object — it doesn't touch the actual cached ID token every backend call
     * attaches (see NetworkModule's authInterceptor), which still carries the *old*
     * `email_verified: false` claim baked in at the moment it was originally issued. Without also
     * forcing a fresh token here, the very next authenticated call this session makes (Home's own
     * feed fetch, moments after landing in the app) would still send that stale token, get
     * rejected by the exact same backend gate this screen just passed, and bounce straight back to
     * this exact screen — which is precisely the loop this line exists to prevent.
     *
     * [onAuthenticated] is only called for a *returning* sign-in (accountCreated false — see
     * [SignInOutcome.NeedsVerification]'s own handling); a fresh sign-up still has the widget/
     * sharing onboarding steps ahead of it, same as it always did before this check existed.
     *
     * Deliberately asks the backend too (via [AuthRepository.resumeSession]), not just Firebase's
     * own local `isEmailVerified` — this button is only reachable before the countdown hits zero
     * (VerifyEmailStep disables it once expired), but that guard is this screen's own wall-clock
     * read, running on the device's own clock. The backend's answer is the one that actually
     * decides whether a verification counted (see FirebaseAuthenticationFilter's own deadline
     * check) — trusting Firebase alone here would let a clock skewed even slightly fast let
     * someone through a request this same deadline was just built to refuse everywhere else. */
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
                    // The backend disagrees — this verification either hasn't landed there yet
                    // (rare timing gap right after clicking the link) or arrived too late to
                    // count. Re-syncing to its own deadline rather than leaving this screen's
                    // countdown at whatever it was already showing.
                    pendingVerificationEmail = outcome.email
                    pendingVerificationDeadlineMillis = outcome.verifyByEpochMillis
                    isCheckingVerification = false
                    verificationCheckError = "Still not verified. Check your inbox and spam folder."
                } else {
                    // A genuine network failure, or NeedsProfile (this account no longer exists —
                    // EmailVerificationExpiryService already deleted it). Neither is "try again in
                    // a second," so this reuses the same message rather than claiming to know
                    // which one happened.
                    isCheckingVerification = false
                    verificationCheckError = "Still not verified. Check your inbox and spam folder."
                }
            } else {
                isCheckingVerification = false
                verificationCheckError = "Still not verified. Check your inbox and spam folder."
            }
        }
    }

    /** The escape hatch for exactly the problem this whole screen exists to prevent: someone who
     * typed an email they don't actually have access to. The real sign-out (clearing the Firebase
     * session, unregistering this device, wiping cached account data) is MainActivity's own
     * onSignOut, passed in from LoginScreen — this only resets this ViewModel's own local state so
     * the login screen it lands back on starts genuinely fresh, not mid-way through a flow for an
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
        // Every in-flight flag, not just the fields: each of these gates its own button
        // (`enabled = !isLoading` and friends), so any one left stuck true from a request that was
        // cut short by the sign-out itself would leave that button permanently dead on a screen
        // that otherwise looks completely normal.
        isLoading = false
        isCheckingVerification = false
        isResendingVerification = false
        errorMessage = welcomeMessage
        step = AuthStep.WELCOME
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
