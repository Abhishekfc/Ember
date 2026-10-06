package com.emigo.app.data.repository

import com.emigo.app.data.firebaseErrorMessage
import com.emigo.app.data.local.TokenStore
import com.emigo.app.data.safeCall
import com.emigo.app.data.remote.EmberApi
import com.emigo.app.data.remote.dto.CompleteProfileRequestDto
import com.emigo.app.data.remote.dto.DeviceTokenRequestDto
import com.emigo.app.data.remote.dto.EmailAvailabilityDto
import com.emigo.app.data.remote.dto.ErrorResponse
import com.emigo.app.data.remote.dto.UsernameAvailabilityDto
import com.emigo.app.data.remote.dto.UsernameLoginLookupDto
import com.emigo.app.data.remote.dto.UserProfileDto
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** What signing in needs the caller to do next.
 *
 * [NeedsProfile] is a Firebase identity with no Emigo profile. LoginViewModel treats it as a plain
 * failed sign-in, worded like a wrong password, so signing in never quietly becomes signing up and
 * the wording can't be used to find out which addresses are registered. It stays a separate
 * outcome here because callers differ (resumeSession ignores it).
 *
 * [NeedsVerification] is a completed profile whose account must verify its email (see
 * UserProfileDto.emailVerificationRequired) and hasn't yet. It is a successful sign-in that isn't
 * allowed into the app yet. [verifyByEpochMillis] is the deadline EmailVerificationExpiryService
 * enforces (see [verificationDeadlineFor]); for an older account it may already be past, and the
 * backend can delete the account on its next check whatever the countdown shows. */
sealed class SignInOutcome {
    data object SignedIn : SignInOutcome()
    data class NeedsProfile(val suggestedDisplayName: String) : SignInOutcome()
    data class NeedsVerification(val email: String, val verifyByEpochMillis: Long) : SignInOutcome()
}

/** True when the server says [profile] still needs email verification. The server is the only side
 * that knows about the deadline, so it alone decides. Used by sign-in and resume (via
 * checkExistingProfile) and by a fresh sign-up's submitUsername.
 *
 * This used to also require Firebase's cached `isEmailVerified` to agree. That let a link clicked
 * after the 10-minute deadline still count as done: Firebase confirms it (it doesn't know about our
 * cutoff), and the check then walked past [UserProfileDto.emailVerificationRequired], which
 * FirebaseAuthenticationFilter deliberately leaves set because the verification came too late. */
fun needsEmailVerification(profile: UserProfileDto): Boolean = profile.emailVerificationRequired

/** Must match EmailVerificationExpiryService's grace period on the backend. This only drives the
 * countdown UI; the backend's copy is what deletes accounts. */
const val EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS: Long = 10 * 60 * 1000L

/** The deadline for [profile], counted from when the account was created, so reopening the screen
 * or the app never resets the countdown. Falls back to a fresh window from now if
 * [UserProfileDto.createdAt] is missing or unparseable (a stale cached profile from before that
 * field existed), which beats crashing. */
fun verificationDeadlineFor(profile: UserProfileDto): Long {
    val createdAtMillis = profile.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
    return (createdAtMillis ?: System.currentTimeMillis()) + EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS
}

class AuthRepository(
    private val api: EmberApi,
    private val tokenStore: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Public, pre-auth email check used by the sign-up email step. */
    suspend fun checkEmailAvailability(email: String): Result<EmailAvailabilityDto> = safeCall {
        val response = api.checkEmailAvailabilityPublic(email)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(Exception("Couldn't check that email"))
        }
    }

    /** Used while picking a username during sign-up, before an account or token exists (see
     * EmberApi.checkUsernameAvailabilityPublic for why UserRepository's authenticated version
     * can't be used). */
    suspend fun checkUsernameAvailability(username: String): Result<UsernameAvailabilityDto> = safeCall {
        val response = api.checkUsernameAvailabilityPublic(username)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(Exception("Couldn't check that username"))
        }
    }

    /**
     * Creates the Firebase identity, then this app's profile on top of it (see [completeProfile]
     * and the backend's `AuthController`).
     *
     * Safe to call again after the second step fails: if a Firebase account is already signed in
     * from an earlier attempt, it is reused instead of calling `createUserWithEmailAndPassword`
     * again, which would fail as a duplicate.
     */
    suspend fun signUp(email: String, password: String, displayName: String, username: String): Result<UserProfileDto> {
        // Reuse an already-signed-in identity only if it is for the same address. Signing in to an
        // account with no profile lands on the name/username steps while still signed in
        // (SignInOutcome.NeedsProfile); backing out to type a different email left that unrelated
        // identity signed in, so the new address was silently ignored and the profile was attached
        // to the wrong account.
        val signedIn = FirebaseAuth.getInstance().currentUser
        if (signedIn != null && !signedIn.email.equals(email.trim(), ignoreCase = true)) {
            FirebaseAuth.getInstance().signOut()
        }
        if (FirebaseAuth.getInstance().currentUser == null) {
            try {
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await()
            } catch (ex: Exception) {
                return Result.failure(Exception(firebaseErrorMessage(ex) ?: "Something went wrong"))
            }
        }
        // Fire-and-forget: failing to send must never fail the sign-up. Verification is still
        // enforced (see needsEmailVerification and the gate in FirebaseAuthenticationFilter), and
        // Resend on the verification screen can retry the mail.
        runCatching { FirebaseAuth.getInstance().currentUser?.sendEmailVerification()?.await() }
        return completeProfile(displayName, username)
    }

    /** The backend call every sign-up ends with, once Firebase has a signed-in identity and only
     * the username is left. No token is passed: NetworkModule's interceptor attaches the current
     * Firebase identity to every request. */
    suspend fun completeProfile(displayName: String, username: String): Result<UserProfileDto> = safeCall {
        val response = api.completeProfile(CompleteProfileRequestDto(displayName, username))
        val body = response.body()
        if (response.isSuccessful && body != null) {
            tokenStore.saveDisplayName(body.displayName)
            Result.success(body)
        } else {
            val message = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
            } ?: "Couldn't finish creating your account (${response.code()})"
            Result.failure(Exception(message))
        }
    }

    /** [identifier] is usually an email, but Firebase has no usernames, so anything without an "@"
     * is first resolved to its email through our backend. An unknown username fails exactly like a
     * wrong password (see [SignInOutcome.NeedsProfile] for why), so the two can't be told apart. */
    suspend fun signIn(identifier: String, password: String): Result<SignInOutcome> {
        val trimmedIdentifier = identifier.trim()
        val email = if (trimmedIdentifier.contains("@")) {
            trimmedIdentifier
        } else {
            val lookup = resolveUsernameForLogin(trimmedIdentifier).getOrElse { return Result.failure(it) }
            // No account has this username: the same failure as a wrong password, not "username
            // not found", so usernames can't be probed.
            lookup.email ?: return Result.failure(Exception("Incorrect email or password"))
        }
        try {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
        } catch (ex: Exception) {
            return Result.failure(Exception(firebaseErrorMessage(ex) ?: "Something went wrong"))
        }
        return checkExistingProfile()
    }

    /** Resolves a username to its email, which only our backend knows (see
     * EmberApi.resolveUsernameForLogin). Separate from [signIn] because a network failure here is
     * different from "no account has this username", and the caller handles them differently. */
    private suspend fun resolveUsernameForLogin(username: String): Result<UsernameLoginLookupDto> = safeCall {
        val response = api.resolveUsernameForLogin(username)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(Exception("Couldn't check that username"))
        }
    }

    /**
     * The third way into the app, besides [signUp] and [signIn]: a session resumed from what
     * Firebase already has on disk, with no sign-in screen. EmberRoot renders the app shell from
     * that cached session on the first frame (hasSavedSession), so without this an account that
     * never verified could be reopened straight into the app. The backend deletes unverified
     * accounts on its own schedule, so there is always a window between the deadline and the row
     * going away.
     *
     * Reloads and force-refreshes first: the cached `isEmailVerified` and ID token date from before
     * the app was killed, so someone who clicked the link while it was closed would still read as
     * unverified. An earlier attempt that answered from stale state bounced people who had really
     * verified.
     *
     * Act only on an explicit [SignInOutcome.NeedsVerification]. A failure here is usually just
     * being offline and must never sign anyone out.
     */
    suspend fun resumeSession(): Result<SignInOutcome> {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.failure(Exception("No session"))
        runCatching { user.reload().await() }
        runCatching { user.getIdToken(true).await() }
        return checkExistingProfile()
    }

    /** Writes the same local echo [checkExistingProfile] writes when it finds
     * [SignInOutcome.NeedsVerification]. A fresh sign-up (LoginViewModel.submitUsername, right
     * after [signUp]) never goes through checkExistingProfile: it already has the profile and
     * decides [needsEmailVerification] itself. Without this, force-quitting in the first seconds
     * after creating an account still flashed into the app shell, because no check had populated
     * the cache yet. */
    suspend fun rememberPendingVerification(email: String, deadlineMillis: Long) {
        FirebaseAuth.getInstance().currentUser?.uid?.let {
            tokenStore.savePendingVerification(it, email, deadlineMillis)
        }
    }

    /** The other half of [rememberPendingVerification], called from
     * [com.emigo.app.ui.auth.LoginViewModel.onEmailVerifiedContinue] when Firebase confirms
     * `isEmailVerified`. That path never calls `GET /users/me`, so the echo would keep saying
     * "pending", and the next cold start would open on the verification screen for an account that
     * is already verified. */
    suspend fun forgetPendingVerification() {
        tokenStore.clearPendingVerification()
    }

    /** Firebase has already confirmed who this is. What's left is whether an Emigo profile exists,
     * and whether it is allowed past the other endpoints yet (`GET /users/me` works either way,
     * see FirebaseAuthenticationFilter's allowlist). Checking [needsEmailVerification] here,
     * instead of letting a blocked account in and waiting for NetworkModule.emailVerificationRequired
     * to catch it on the next failed request, avoids a brief flash into the app before bouncing
     * back. */
    private suspend fun checkExistingProfile(): Result<SignInOutcome> = safeCall {
        val response = api.getMyProfile()
        val body = response.body()
        when {
            response.isSuccessful && body != null && needsEmailVerification(body) -> {
                val deadline = verificationDeadlineFor(body)
                // Local echo of this outcome (see TokenStore.PendingVerification), so MainActivity's
                // first frame on a cold start can already show this screen instead of waiting for
                // another round trip. currentUser is never null here: getMyProfile only succeeds
                // when someone is signed in.
                FirebaseAuth.getInstance().currentUser?.uid?.let {
                    tokenStore.savePendingVerification(it, body.email, deadline)
                }
                Result.success(SignInOutcome.NeedsVerification(body.email, deadline))
            }
            response.isSuccessful && body != null -> {
                tokenStore.saveDisplayName(body.displayName)
                // Verified (or never needed to be): a leftover echo is now stale and must not
                // claim otherwise on a later cold start.
                tokenStore.clearPendingVerification()
                Result.success(SignInOutcome.SignedIn)
            }
            response.code() == 401 -> Result.success(
                SignInOutcome.NeedsProfile(FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()),
            )
            else -> Result.failure(Exception("Something went wrong (${response.code()})"))
        }
    }

    /** Firebase sends the reset email and hosts the reset page; this app's backend isn't involved. */
    suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
        Result.success(Unit)
    } catch (ex: Exception) {
        Result.failure(Exception(firebaseErrorMessage(ex) ?: "Couldn't send that email"))
    }

    /** Called when a new FCM token arrives (see EmberFirebaseMessagingService) and when a session
     * becomes authenticated (fresh login, or a valid session found at cold start, see
     * EmberRoot), since either can be the first time a token and a signed-in user coexist. A
     * failure only means no pushes until the next attempt, so callers fire and forget. */
    suspend fun registerDeviceToken(fcmToken: String): Result<Unit> = safeCall {
        val response = api.registerDevice(DeviceTokenRequestDto(fcmToken))
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Couldn't register device (${response.code()})"))
        }
    }

    /**
     * Detaches this device from the signed-out account's push list.
     *
     * Without it, the device's FCM token stayed attached server-side after sign-out, so the old
     * account kept pushing "<friend> sent you a photo", with the sender's real name, to a phone on
     * the login screen or in someone else's hands. The device can't suppress that: the server sends
     * to the token, and signing out of Firebase doesn't affect it.
     *
     * Must run before the Firebase session is torn down, since the call is authenticated. Best
     * effort: if it fails (an offline sign-out), the account keeps the stale token until FCM
     * reports it dead or the next account on this device reclaims it, so a failure never blocks
     * signing out.
     */
    suspend fun unregisterDeviceToken(fcmToken: String): Result<Unit> = safeCall {
        val response = api.unregisterDevice(DeviceTokenRequestDto(fcmToken))
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Couldn't unregister device (${response.code()})"))
        }
    }
}
