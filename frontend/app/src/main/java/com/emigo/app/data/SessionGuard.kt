package com.emigo.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.tasks.await

/**
 * Tells background work (a push arriving while the app is closed) whether the account is still
 * signed in and still accepted by Firebase.
 *
 * Changing a password makes Firebase end the account's sessions everywhere, but a phone only finds
 * out when it next asks Firebase for a fresh token, and until then it still looks signed in. So a
 * push for that account (a friend's new photo) would otherwise keep updating the widget, and show a
 * notification, on a phone that has been logged out. [isSessionValid] asks for a fresh token to find
 * out, at most once in a while, and signs out on a definite answer that the session is over.
 *
 * Only a definite refusal counts. No connection, a slow network or any other error means "still
 * signed in", so a bad moment on the network never wipes someone's widget.
 */
object SessionGuard {

    /** How long a "still valid" answer is trusted before asking Firebase again, so a burst of
     * pushes costs one token refresh, not one each. */
    internal const val RECHECK_AFTER_MILLIS = 10 * 60 * 1000L

    @Volatile
    private var lastConfirmedAtMillis = 0L

    suspend fun isSessionValid(
        auth: FirebaseAuth = FirebaseAuth.getInstance(),
        nowMillis: () -> Long = System::currentTimeMillis,
    ): Boolean {
        val user = auth.currentUser ?: return false
        if (!shouldRecheck(nowMillis(), lastConfirmedAtMillis)) return true
        return try {
            // true = skip the cached token and ask Firebase, which is where a revoked session shows.
            user.getIdToken(true).await()
            lastConfirmedAtMillis = nowMillis()
            true
        } catch (e: Exception) {
            if (isSessionEnded(e)) {
                auth.signOut()
                false
            } else {
                true
            }
        }
    }

    internal fun shouldRecheck(nowMillis: Long, lastConfirmedAtMillis: Long): Boolean =
        nowMillis - lastConfirmedAtMillis >= RECHECK_AFTER_MILLIS

    /** Firebase's definite answers that this account's session is over: the user is gone or
     * disabled, or their credentials were revoked (a password change). Anything else is not one. */
    internal fun isSessionEnded(error: Throwable): Boolean =
        error is FirebaseAuthInvalidUserException || error is FirebaseAuthInvalidCredentialsException
}
