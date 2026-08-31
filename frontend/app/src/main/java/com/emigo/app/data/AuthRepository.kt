package com.emigo.app.data

import com.emigo.app.data.local.TokenStore
import com.emigo.app.data.remote.EmberApi
import com.emigo.app.data.remote.dto.CompleteProfileRequestDto
import com.emigo.app.data.remote.dto.DeviceTokenRequestDto
import com.emigo.app.data.remote.dto.EmailAvailabilityDto
import com.emigo.app.data.remote.dto.ErrorResponse
import com.emigo.app.data.remote.dto.UsernameAvailabilityDto
import com.emigo.app.data.remote.dto.UserProfileDto
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** What signing in needs the caller to do next.
 *
 * [NeedsProfile] is a Firebase identity with no Emigo profile behind it. LoginViewModel treats
 * this as a plain failed sign-in — same wording as a wrong password, deliberately — rather than
 * continuing into the sign-up steps: signing in must never quietly become signing up, and the
 * identical wording is also what stops the difference being used to work out which addresses are
 * registered. It stays a distinct outcome rather than collapsing into a failure here because
 * AuthRepository can't know how a caller wants to treat it (resumeSession, for instance, ignores
 * it entirely).
 *
 * [NeedsVerification] is a real, already-completed profile whose account requires email
 * verification (see UserProfileDto.emailVerificationRequired) and hasn't done it yet — distinct
 * from [NeedsProfile] (no account at all) and from a failure (this *is* a successful sign-in,
 * just not one that's allowed to reach the rest of the app yet). [verifyByEpochMillis] is the same
 * deadline EmailVerificationExpiryService enforces server-side (see [verificationDeadlineFor]) —
 * an account reached through this path could already be older than the grace period by the time
 * someone signs back into it, in which case the backend may delete it within its own next check
 * regardless of what this screen's countdown shows. */
sealed class SignInOutcome {
    data object SignedIn : SignInOutcome()
    data class NeedsProfile(val suggestedDisplayName: String) : SignInOutcome()
    data class NeedsVerification(val email: String, val verifyByEpochMillis: Long) : SignInOutcome()
}

/** True when [profile]'s account requires email verification and Firebase's own (locally cached)
 * record for the current identity says that hasn't happened — the one check every entry point
 * into the app (fresh sign-up, fresh sign-in, and NetworkModule.emailVerificationRequired for an
 * already-signed-in session) needs to make the same way. Firebase's cached flag can be stale right
 * after the user actually clicks the email link — see EmailVerificationScreen's own "I've
 * verified" action, which forces a reload before re-checking this. */
fun needsEmailVerification(profile: UserProfileDto): Boolean =
    profile.emailVerificationRequired && FirebaseAuth.getInstance().currentUser?.isEmailVerified != true

/** Must match EmailVerificationExpiryService's own grace period on the backend exactly — this is
 * purely the number the countdown UI shows, the backend's own copy of it is the one that actually
 * deletes anything. */
const val EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS: Long = 10 * 60 * 1000L

/** The real deadline for [profile] specifically — based on when the account was actually created,
 * not on whenever this happens to be called, so re-opening the verification screen (or the app
 * itself) never resets the countdown. Falls back to a fresh window from right now only if
 * [UserProfileDto.createdAt] is missing or unparseable, which should never genuinely happen for a
 * real response — a stale locally-cached profile from before that field existed is the one
 * realistic case, and a fresh countdown is a reasonable default for that, not a crash. */
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

    /** Used while picking a username during sign-up, before an account/token exists — see
     * EmberApi.checkUsernameAvailabilityPublic for why this can't go through UserRepository's
     * authenticated equivalent. */
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
     * Creates the Firebase identity, then this app's own profile on top of it (see
     * [completeProfile] for the second half, and the backend's own `AuthController` for where
     * that lands).
     *
     * Safe to call again after a failure on the *second* half specifically: if a Firebase account
     * already exists and is signed in — from a previous attempt that got this far before losing
     * the network or the app closing — this reuses it instead of calling
     * `createUserWithEmailAndPassword` again, which would fail as a duplicate identity.
     */
    suspend fun signUp(email: String, password: String, displayName: String, username: String): Result<UserProfileDto> {
        // Reusing an already-signed-in identity is only correct when it's the *same* address this
        // sign-up is for. It isn't always: signing in to an account with no profile yet lands on
        // the name/username steps (SignInOutcome.NeedsProfile) while still signed in, and backing
        // out of there to type a different email leaves a signed-in identity that has nothing to
        // do with what's now on screen. Without this check the new address was silently ignored
        // and the profile — username, display name, everything — was attached to whichever
        // identity happened to still be signed in, under an email the person never typed here.
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
        // Fire-and-forget, deliberately not awaited for its own result: a failure to send this
        // must never block or fail the sign-up itself. Verification is still enforced — see
        // needsEmailVerification and the server-side gate in FirebaseAuthenticationFilter — this
        // is only about the mail going out, which Resend on the verification screen can retry.
        runCatching { FirebaseAuth.getInstance().currentUser?.sendEmailVerification()?.await() }
        return completeProfile(displayName, username)
    }

    /** The one backend call every sign-up ends with, once Firebase has a real, signed-in identity
     * and all that's left is choosing a username. No token is passed explicitly — NetworkModule's
     * own interceptor already attaches whatever Firebase considers the current signed-in identity
     * to every request, this one included. */
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

    suspend fun signIn(email: String, password: String): Result<SignInOutcome> {
        try {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
        } catch (ex: Exception) {
            return Result.failure(Exception(firebaseErrorMessage(ex) ?: "Something went wrong"))
        }
        return checkExistingProfile()
    }

    /**
     * The third entry point into the app, alongside [signUp] and [signIn]: a returning session
     * resumed from whatever Firebase already had on disk, with no sign-in screen involved at all.
     * MainActivity renders the app shell optimistically from that cached session on the very first
     * frame (see hasSavedSession there), so without this an account that never verified could
     * simply be reopened straight into the app — every request inside it would fail, but it would
     * be *in*, which is exactly the state this whole feature exists to prevent. That gap was real:
     * the backend deletes an unverified account on its own schedule, so there's always a window
     * between the deadline passing and the row actually going away.
     *
     * Reloads and force-refreshes first, deliberately: the locally cached `isEmailVerified` and
     * the cached ID token are both snapshots from before the app was last killed, and someone who
     * clicked the link while the app was closed would otherwise still read as unverified here.
     * Answering this from stale state is what made an earlier attempt at catching this bounce
     * people who had genuinely already verified.
     *
     * Only ever act on an explicit [SignInOutcome.NeedsVerification] result from this — a failure
     * here is very often just being offline, which must never turn into signing someone out.
     */
    suspend fun resumeSession(): Result<SignInOutcome> {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.failure(Exception("No session"))
        runCatching { user.reload().await() }
        runCatching { user.getIdToken(true).await() }
        return checkExistingProfile()
    }

    /** Firebase already confirmed who this is by the time [signIn] calls this — the open
     * questions are whether an Emigo profile exists yet for them, and (GET /users/me succeeds
     * either way — see FirebaseAuthenticationFilter's own allowlist for that endpoint
     * specifically) whether it's actually allowed past every other endpoint yet. Checking
     * [needsEmailVerification] right here, rather than letting a genuinely-blocked account
     * through into the app and relying solely on NetworkModule.emailVerificationRequired to catch
     * it once the next real request fails, is what avoids a real, if brief, flash into the app
     * before bouncing back out to this same screen. */
    private suspend fun checkExistingProfile(): Result<SignInOutcome> = safeCall {
        val response = api.getMyProfile()
        val body = response.body()
        when {
            response.isSuccessful && body != null && needsEmailVerification(body) -> Result.success(
                SignInOutcome.NeedsVerification(body.email, verificationDeadlineFor(body)),
            )
            response.isSuccessful && body != null -> {
                tokenStore.saveDisplayName(body.displayName)
                Result.success(SignInOutcome.SignedIn)
            }
            response.code() == 401 -> Result.success(
                SignInOutcome.NeedsProfile(FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()),
            )
            else -> Result.failure(Exception("Something went wrong (${response.code()})"))
        }
    }

    /** Firebase sends the reset email itself and hosts the reset page — nothing here touches this
     * app's own backend at all. */
    suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
        Result.success(Unit)
    } catch (ex: Exception) {
        Result.failure(Exception(firebaseErrorMessage(ex) ?: "Couldn't send that email"))
    }

    /** Called whenever a fresh FCM token becomes available (see EmberFirebaseMessagingService)
     * and once whenever a session becomes authenticated (fresh login, or an already-valid
     * session found at cold start — see MainActivity), since either moment can be the first time
     * a token and a signed-in user actually coexist. Fire-and-forget from the caller's side: a
     * failure here just means this device won't receive pushes until the next successful
     * registration attempt, not a user-facing error. */
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
     * Without it, signing out left the device's FCM token still attached server-side, so the
     * account that was just signed out of kept pushing "<friend> sent you a photo" — with the
     * sender's real name in the notification shade — to a phone now sitting on the login screen,
     * or in someone else's hands. Nothing on the device could suppress those: the token is what
     * the server sends to, and signing out of Firebase has no effect on it.
     *
     * Must run *before* the Firebase session is torn down, since this call is itself
     * authenticated. Best effort — if it fails (offline sign-out being the obvious case) the
     * account simply keeps the stale token until FCM reports it dead or the next account to sign
     * in on this device reclaims it, which is exactly the old behaviour, so a failure never blocks
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
