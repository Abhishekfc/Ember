package com.ember.backend.security

import com.ember.backend.repository.UserRepository
import com.ember.backend.service.UNVERIFIED_ACCOUNT_GRACE_PERIOD
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/**
 * Replaces the old JwtAuthenticationFilter now that Firebase Authentication owns identity rather
 * than this backend issuing and verifying its own JWTs. The `Authorization: Bearer` header every
 * request carries is now a Firebase ID token — the same one the client got straight from the
 * Firebase SDK at sign-in and silently refreshes every hour — not a token this server ever minted.
 *
 * Deliberately does *not* auto-create a [com.ember.backend.model.User] row for a verified token
 * with no matching [com.ember.backend.model.User.firebaseUid]. That case — a real, verified
 * Firebase identity with no Emigo profile yet — means either a brand-new sign-up that hasn't
 * chosen a username yet, or (during the one-time migration window) an existing account not yet
 * imported into Firebase. Either way nothing here knows what username/display name to give them,
 * so the request is simply left unauthenticated, same as an invalid token: every ordinary endpoint
 * correctly 401s, and only AuthController's own complete-profile endpoint — which verifies the
 * token itself rather than relying on this filter — can act on that state.
 *
 * The old custom-JWT concept of a per-account revocation cutoff
 * ([com.ember.backend.model.User.tokensValidFrom]) has no equivalent here yet: Firebase Admin
 * supports the same idea natively (`revokeRefreshTokens`, checked via `verifyIdToken(token, true)`)
 * but nothing calls it yet, since password changes now happen entirely client-side through the
 * Firebase SDK with no backend involvement to hook that into. Revisit once that flow exists.
 */
@Component
class FirebaseAuthenticationFilter(
    private val tokenVerifier: FirebaseTokenVerifier,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substringAfter("Bearer ").trim()
            val verified = tokenVerifier.verify(token)
            val user = verified?.let { userRepository.findByFirebaseUid(it.uid) }
            if (user != null) {
                // Verification within the deadline is what actually clears the pending flag below
                // — this is the *only* place it ever gets cleared (EmailVerificationExpiryService's
                // own sweep never clears it any more, only deletes what's still set once its
                // deadline has passed — see that class's own doc comment). Guarded on the flag
                // itself, so this is a single write once per account and then never again, not a
                // write on every authenticated request. Swallowed on failure on purpose: this is
                // housekeeping on the authentication path, and authentication must not start
                // failing because a bookkeeping write didn't land — a miss here just means this
                // same write is retried on the account's very next authenticated request instead.
                if (user.emailVerificationRequired) {
                    // Gated on the exact same deadline EmailVerificationExpiryService enforces
                    // (see UNVERIFIED_ACCOUNT_GRACE_PERIOD's own doc comment) — not just on
                    // whether Firebase currently says verified. A verification clicked *after*
                    // that deadline still makes Firebase's own reload happily report verified (it
                    // has no idea this app enforces a stricter cutoff of its own), so clearing the
                    // flag on that signal alone let a late verification rescue an account this
                    // backend had already decided to delete — confirmed by testing exactly that:
                    // verifying only once the on-screen countdown had already failed still left
                    // the account alive forever afterward. Once past its own deadline, this
                    // account is treated as expired regardless of what Firebase reports, and stays
                    // blocked so the sweep is free to delete it without anything here having
                    // already granted it permanent access on some earlier request.
                    val pastDeadline = Instant.now().isAfter(user.createdAt.plus(UNVERIFIED_ACCOUNT_GRACE_PERIOD))
                    if (verified.emailVerified && !pastDeadline) {
                        runCatching { userRepository.clearEmailVerificationRequired(user.id) }
                    } else {
                        // A real, matched identity that must verify its email and hasn't (in
                        // time) — blocked from everything except reading its own profile (which is
                        // what lets the client know it's in this state at all, and show the right
                        // screen instead of a wall of failing requests). GET /users/me
                        // specifically, not the whole prefix: an update to the profile is still a
                        // mutation this account shouldn't be able to make yet. Written directly
                        // here rather than thrown as an ApiException, since this filter runs
                        // before Spring MVC's dispatch — GlobalExceptionHandler's
                        // @ExceptionHandlers never see anything a filter short-circuits on.
                        val isOwnProfileRead = request.method == "GET" && request.requestURI == "/users/me"
                        if (!isOwnProfileRead) {
                            response.status = HttpServletResponse.SC_FORBIDDEN
                            response.contentType = "application/json"
                            // A dedicated header, not something baked into the JSON body — the
                            // client's sessionExpired-style interceptor only needs a single cheap
                            // header check to tell this apart from every other 403 in the app
                            // (e.g. GoldSubscriptionRequiredException, also a plain 403), without
                            // coupling it to the exact error message text.
                            response.setHeader("X-Ember-Error", "EMAIL_NOT_VERIFIED")
                            response.writer.write(
                                """{"status":403,"error":"Forbidden","message":"Please verify your email to continue"}""",
                            )
                            return
                        }
                    }
                }
                if (SecurityContextHolder.getContext().authentication == null) {
                    val authentication = UsernamePasswordAuthenticationToken(
                        AuthenticatedUser(user.id),
                        null,
                        emptyList(),
                    )
                    SecurityContextHolder.getContext().authentication = authentication
                }
            }
        }
        filterChain.doFilter(request, response)
    }
}
