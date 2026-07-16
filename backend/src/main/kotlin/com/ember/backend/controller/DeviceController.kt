package com.ember.backend.controller

import com.ember.backend.dto.DeviceTokenRequest
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.DeviceTokenService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/devices")
class DeviceController(private val deviceTokenService: DeviceTokenService) {

    @PostMapping("/register")
    fun register(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody request: DeviceTokenRequest,
    ): ResponseEntity<Void> {
        deviceTokenService.registerToken(me.id, request.fcmToken)
        return ResponseEntity.noContent().build()
    }
}
