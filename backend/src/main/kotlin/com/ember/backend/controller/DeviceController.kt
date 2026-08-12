package com.ember.backend.controller

import com.ember.backend.dto.DeviceTokenRequest
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.DeviceTokenService
import com.ember.backend.service.RateLimiterService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/devices")
class DeviceController(
    private val deviceTokenService: DeviceTokenService,
    private val rateLimiterService: RateLimiterService,
) {

    @PostMapping("/register")
    fun register(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody request: DeviceTokenRequest,
    ): ResponseEntity<Void> {
        // A real client calls this once per sign-in and again only when FCM rotates the device's
        // token — a handful of times a day at the very most. The limit exists because nothing
        // stopped an authenticated caller from calling it in a loop with fresh random strings; the
        // per-account cap in DeviceTokenService bounds the damage, this bounds the write volume
        // it takes to reach it.
        rateLimiterService.checkLimit("device-register:${me.id}", maxAttempts = 30, window = Duration.ofHours(1))
        deviceTokenService.registerToken(me.id, request.fcmToken)
        return ResponseEntity.noContent().build()
    }

    /** Called on sign-out so this device stops receiving the signed-out account's notifications —
     * see DeviceTokenService.unregisterToken. */
    @PostMapping("/unregister")
    fun unregister(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody request: DeviceTokenRequest,
    ): ResponseEntity<Void> {
        deviceTokenService.unregisterToken(me.id, request.fcmToken)
        return ResponseEntity.noContent().build()
    }
}
