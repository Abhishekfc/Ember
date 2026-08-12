package com.ember.backend.security

import com.ember.backend.model.User
import com.ember.backend.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
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
            val verified = jwtService.parseToken(token)
            if (verified != null && SecurityContextHolder.getContext().authentication == null && isStillValid(verified)) {
                val authentication = UsernamePasswordAuthenticationToken(
                    AuthenticatedUser(verified.userId),
                    null,
                    emptyList(),
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        filterChain.doFilter(request, response)
    }

    /**
     * The half of authentication that a signature check alone can't do: whether this
     * still-unexpired token has been revoked since it was issued.
     *
     * Access tokens are stateless and live for 7 days, so before this the only way a token stopped
     * working was by expiring. Changing a password — the thing you do precisely because someone
     * else may have your token — left every outstanding session working for the rest of that week.
     * Comparing the token's own `iat` against the account's [User.tokensValidFrom] cutoff revokes
     * all of them at once (see the V11 migration).
     *
     * This costs one primary-key lookup per authenticated request, which is the unavoidable price
     * of revocable stateless tokens; it is deliberately *not* cached, because a cache would keep
     * accepting a revoked token for the length of its TTL, which is the one thing this exists to
     * prevent.
     *
     * A token naming an account that no longer exists now fails here too. That used to authenticate
     * fine and then produce a confusing "User not found" 404 from whichever endpoint was called; as
     * a 401 the client's existing session-expiry handling signs the device out cleanly instead.
     */
    private fun isStillValid(verified: JwtService.VerifiedToken): Boolean {
        val user = userRepository.findById(verified.userId).orElse(null) ?: return false
        val cutoff = user.tokensValidFrom ?: return true
        // Not `isAfter`: `iat` is serialized with whole-second precision, so a token issued in the
        // same second as the cutoff must still count as at-or-after it — otherwise the replacement
        // token handed back by the password change that set the cutoff would itself be rejected.
        return !verified.issuedAt.isBefore(cutoff)
    }
}
