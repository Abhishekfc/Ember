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
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** What signing in (email/password or Google) needs the caller to do next. A brand-new identity
 * with no Emigo profile yet — an interrupted sign-up resumed later, or genuinely the first time
 * this person has used Emigo via Google — is [NeedsProfile], not a failure: Firebase has already
 * confirmed who they are, all that's missing is a username, so LoginViewModel routes this into
 * the same username step sign-up already uses rather than treating it as an error. */
sealed class SignInOutcome {
    data object SignedIn : SignInOutcome()
    data class NeedsProfile(val suggestedDisplayName: String) : SignInOutcome()
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
        if (FirebaseAuth.getInstance().currentUser == null) {
            try {
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await()
            } catch (ex: Exception) {
                return Result.failure(Exception(firebaseErrorMessage(ex) ?: "Something went wrong"))
            }
        }
        // Fire-and-forget, deliberately not awaited for its own result: this app doesn't gate
        // anything on email verification (see AuthService's own doc comment on going lenient), so
        // a failure to send this must never block or fail the sign-up itself.
        runCatching { FirebaseAuth.getInstance().currentUser?.sendEmailVerification()?.await() }
        return completeProfile(displayName, username)
    }

    /** The one backend call every sign-up (email/password or Google) ends with, once Firebase has
     * a real, signed-in identity and all that's left is choosing a username. No token is passed
     * explicitly — NetworkModule's own interceptor already attaches whatever Firebase considers
     * the current signed-in identity to every request, this one included. */
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

    /** Exchanges a Google ID token (from the classic Google Sign-In client — see LoginScreen) for
     * a Firebase credential. */
    suspend fun signInWithGoogle(googleIdToken: String): Result<SignInOutcome> {
        try {
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).await()
        } catch (ex: Exception) {
            return Result.failure(Exception(firebaseErrorMessage(ex) ?: "Google sign-in failed"))
        }
        return checkExistingProfile()
    }

    /** Firebase already confirmed who this is by the time either [signIn] or [signInWithGoogle]
     * calls this — the only open question is whether an Emigo profile exists yet for them. */
    private suspend fun checkExistingProfile(): Result<SignInOutcome> = safeCall {
        val response = api.getMyProfile()
        val body = response.body()
        when {
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
