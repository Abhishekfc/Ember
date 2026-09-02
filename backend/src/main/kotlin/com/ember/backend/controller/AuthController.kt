package com.ember.backend.controller

import com.ember.backend.dto.CompleteProfileRequest
import com.ember.backend.dto.EmailAvailability
import com.ember.backend.dto.UserProfile
import com.ember.backend.dto.UsernameAvailability
import com.ember.backend.dto.UsernameLoginLookup
import com.ember.backend.security.FirebaseTokenVerifier
import com.ember.backend.service.AuthService
import com.ember.backend.service.RateLimiterService
import com.ember.backend.service.UserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/** The `/auth` endpoints are the only `permitAll()` surface in the app (see SecurityConfig) —
 * every one of them is rate-limited per client IP, since none has any brute-force/mass-abuse
 * protection otherwise. Sign-in and sign-up themselves no longer happen here at all — that's
 * Firebase Authentication's job on the client now — so what's left is: finishing a new profile
 * once Firebase has already verified who someone is, and the availability checks that flow needs
 * along the way. */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val tokenVerifier: FirebaseTokenVerifier,
    private val rateLimiterService: RateLimiterService,
) {

    /**
     * The one step this backend still owns after sign-up: choosing a username and display name.
     * Called once, right after the client finishes creating (and, for the email/password
     * provider, verifying) the account directly with Firebase — everything about *who* this is
     * comes from the bearer token itself, verified here by hand rather than through the normal
     * [com.ember.backend.security.FirebaseAuthenticationFilter] path, since that filter can only
     * authenticate identities that already have a matching Emigo profile, which by definition
     * this one doesn't yet.
     */
    @PostMapping("/complete-profile")
    fun completeProfile(
        // Optional at the Spring level deliberately — a request with no header at all must reach
        // this same "not authenticated" path as an invalid one, not throw a framework-level
        // MissingRequestHeaderException that the global handler has no specific case for and
        // surfaces as a raw 500. Both a missing and a garbage token mean the same thing here.
        @RequestHeader("Authorization", required = false) authorization: String?,
        @Valid @RequestBody request: CompleteProfileRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<UserProfile> {
        rateLimiterService.checkLimit("complete-profile:${httpRequest.remoteAddr}", maxAttempts = 10, window = Duration.ofHours(1))
        val token = authorization?.removePrefix("Bearer ")?.trim()
        val verified = token?.let { tokenVerifier.verify(it) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.completeProfile(verified, request))
    }

    // Called on every keystroke (client-debounced) while someone's picking a username during
    // sign-up, well before an account/token exists — generous limit since a single person typing
    // out a few candidates easily fires a dozen+ checks, but still bounded per IP.
    @GetMapping("/username-availability")
    fun checkUsernameAvailability(@RequestParam username: String, httpRequest: HttpServletRequest): UsernameAvailability {
        rateLimiterService.checkLimit("username-availability:${httpRequest.remoteAddr}", maxAttempts = 60, window = Duration.ofMinutes(10))
        return userService.checkUsernameAvailabilityPublic(username)
    }

    // Checked once per email step (on continue), not per keystroke, so a much tighter limit than
    // the username check above is enough. Rate limited at all because this can otherwise be used
    // to test whether a given address has an Emigo account.
    @GetMapping("/email-availability")
    fun checkEmailAvailability(@RequestParam email: String, httpRequest: HttpServletRequest): EmailAvailability {
        rateLimiterService.checkLimit("email-availability:${httpRequest.remoteAddr}", maxAttempts = 20, window = Duration.ofMinutes(10))
        return userService.checkEmailAvailabilityPublic(email)
    }

    // Firebase signs in by email only — no concept of a username at all — so a username typed
    // into the login screen has to be resolved back to its email here before the client can hand
    // it to Firebase. Same rate limit as email-availability above, and for the same reason: this
    // is one more way to test whether a given identifier has an Emigo account behind it.
    @GetMapping("/username-login-lookup")
    fun resolveUsernameForLogin(@RequestParam username: String, httpRequest: HttpServletRequest): UsernameLoginLookup {
        rateLimiterService.checkLimit("username-login-lookup:${httpRequest.remoteAddr}", maxAttempts = 20, window = Duration.ofMinutes(10))
        return userService.resolveUsernameForLogin(username)
    }
}
