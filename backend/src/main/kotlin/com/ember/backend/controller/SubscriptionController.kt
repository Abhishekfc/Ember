package com.ember.backend.controller

import com.ember.backend.dto.SubscriptionStatusResponse
import com.ember.backend.dto.SubscriptionVerifyRequest
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.SubscriptionService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/subscription")
class SubscriptionController(private val subscriptionService: SubscriptionService) {

    @GetMapping("/status")
    fun status(@AuthenticationPrincipal me: AuthenticatedUser): SubscriptionStatusResponse =
        subscriptionService.getStatus(me.id)

    @PostMapping("/verify")
    fun verify(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody request: SubscriptionVerifyRequest,
    ): SubscriptionStatusResponse = subscriptionService.verify(me.id, request.productId, request.purchaseToken)
}
