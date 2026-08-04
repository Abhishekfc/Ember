package com.ember.backend.controller

import com.ember.backend.dto.AuthResponse
import com.ember.backend.dto.EmailAvailability
import com.ember.backend.dto.LoginRequest
import com.ember.backend.dto.RegisterRequest
import com.ember.backend.dto.UsernameAvailability
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/** The `/auth` endpoints are the only `permitAll()` surface in the app (see SecurityConfig) —
 * both are rate-limited per client IP since neither had any brute-force/mass-registration
 * protection before. Limits are deliberately generous (a real user mistyping a password a few
 * times, or a household registering several accounts, should never be the one who gets blocked)
 * but bound how much unattended guessing/scripting is possible per IP per window. */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val rateLimiterService: RateLimiterService,
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest, httpRequest: HttpServletRequest): ResponseEntity<AuthResponse> {
        rateLimiterService.checkLimit("register:${httpRequest.remoteAddr}", maxAttempts = 10, window = Duration.ofHours(1))
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, httpRequest: HttpServletRequest): ResponseEntity<AuthResponse> {
        rateLimiterService.checkLimit("login:${httpRequest.remoteAddr}", maxAttempts = 20, window = Duration.ofMinutes(15))
        return ResponseEntity.ok(authService.login(request))
    }

    // Called on every keystroke (client-debounced) while someone's picking a username during
    // registration, well before an account/token exists — generous limit since a single person
    // typing out a few candidates easily fires a dozen+ checks, but still bounded per IP.
    @GetMapping("/username-availability")
    fun checkUsernameAvailability(@RequestParam username: String, httpRequest: HttpServletRequest): UsernameAvailability {
        rateLimiterService.checkLimit("username-availability:${httpRequest.remoteAddr}", maxAttempts = 60, window = Duration.ofMinutes(10))
        return userService.checkUsernameAvailabilityPublic(username)
    }

    // Checked once per email step (on continue), not per keystroke, so a much tighter limit than
    // the username check above is enough. Rate limited at all because this can otherwise be used
    // to test whether a given address has an Ember account.
    @GetMapping("/email-availability")
    fun checkEmailAvailability(@RequestParam email: String, httpRequest: HttpServletRequest): EmailAvailability {
        rateLimiterService.checkLimit("email-availability:${httpRequest.remoteAddr}", maxAttempts = 20, window = Duration.ofMinutes(10))
        return userService.checkEmailAvailabilityPublic(email)
    }
}
