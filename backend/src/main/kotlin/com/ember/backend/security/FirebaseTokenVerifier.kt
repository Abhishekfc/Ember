package com.ember.backend.security

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.springframework.stereotype.Component

/** Everything this backend ever needs out of a Firebase ID token, once verified. */
data class VerifiedFirebaseToken(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
)

/**
 * Verifies a raw Firebase ID token string — the one thing every authenticated request now carries
 * in its `Authorization: Bearer` header, replacing the custom JWTs this backend used to issue
 * itself (see [FirebaseAuthenticationFilter] for where that happens on every request).
 *
 * Used from two places, which is why this isn't folded directly into the filter: the filter
 * itself (for every ordinary request, where a matching local [com.ember.backend.model.User] row
 * is expected to already exist), and [com.ember.backend.controller.AuthController]'s own
 * complete-profile endpoint (for the one request that, by definition, arrives with a *valid*
 * Firebase identity but *no* local row yet — the filter alone can't authenticate that case since
 * it has no [com.ember.backend.model.User.id] to hand back, so that endpoint verifies the token
 * itself rather than relying on the filter to have already done it).
 */
@Component
class FirebaseTokenVerifier(private val firebaseApp: FirebaseApp?) {

    /** Null if Firebase isn't configured, the token is malformed, expired, or its signature
     * doesn't check out — every failure mode collapses to "not authenticated," the same as an
     * unparseable custom JWT did before. */
    fun verify(token: String): VerifiedFirebaseToken? {
        val app = firebaseApp ?: return null
        return try {
            val decoded = FirebaseAuth.getInstance(app).verifyIdToken(token)
            VerifiedFirebaseToken(
                uid = decoded.uid,
                // Firebase guarantees this is present and this exact identity's own address for
                // both providers this app enables (email/password, Google) — there's no path
                // where a verified token names an email it doesn't actually control.
                email = decoded.email,
                emailVerified = decoded.isEmailVerified,
            )
        } catch (ex: Exception) {
            null
        }
    }
}
