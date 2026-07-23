package com.ember.backend.controller

import com.ember.backend.dto.AuthResponse
import com.ember.backend.dto.LoginRequest
import com.ember.backend.dto.RegisterRequest
import com.ember.backend.service.AuthService
import com.ember.backend.service.RateLimiterService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
}
