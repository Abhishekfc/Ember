package com.ember.backend.controller

import com.ember.backend.dto.BlockedUserSummary
import com.ember.backend.dto.ReportUserRequest
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.BlockService
import com.ember.backend.service.RateLimiterService
import com.ember.backend.service.ReportService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

/** Separate from UserController (which only ever operates on the caller's own `/users/me`) and
 * from FriendsController (relationship management) — blocking and reporting are their own
 * "safety" concern, about a specific *other* user, not a friendship's lifecycle. */
@RestController
@RequestMapping("/users")
class UserSafetyController(
    private val blockService: BlockService,
    private val reportService: ReportService,
    private val rateLimiterService: RateLimiterService,
) {

    @GetMapping("/blocked")
    fun listBlocked(@AuthenticationPrincipal me: AuthenticatedUser): List<BlockedUserSummary> =
        blockService.getBlockedUsers(me.id)

    @PostMapping("/{userId}/block")
    fun blockUser(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable userId: UUID,
    ): ResponseEntity<Void> {
        blockService.block(me.id, userId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{userId}/block")
    fun unblockUser(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable userId: UUID,
    ): ResponseEntity<Void> {
        blockService.unblock(me.id, userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{userId}/report")
    fun reportUser(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable userId: UUID,
        @Valid @RequestBody request: ReportUserRequest,
    ): ResponseEntity<Void> {
        // A generous but real ceiling — enough for genuine reports against several different
        // accounts in a day, not enough to spam any one account or the moderation queue as a
        // whole. Same rate-limiter/pattern as FriendsController's own friend-request limit.
        rateLimiterService.checkLimit("report:${me.id}", maxAttempts = 10, window = Duration.ofHours(24))
        reportService.report(me.id, userId, request)
        return ResponseEntity.noContent().build()
    }
}
