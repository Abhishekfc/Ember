package com.ember.backend.security

import com.ember.backend.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

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
            if (user != null && SecurityContextHolder.getContext().authentication == null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    AuthenticatedUser(user.id),
                    null,
                    emptyList(),
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        filterChain.doFilter(request, response)
    }
}
