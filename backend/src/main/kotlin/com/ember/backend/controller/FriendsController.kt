package com.ember.backend.controller

import com.ember.backend.dto.FriendAcceptRequest
import com.ember.backend.dto.FriendRequestRequest
import com.ember.backend.dto.FriendSearchResult
import com.ember.backend.dto.FriendSummary
import com.ember.backend.dto.Page
import com.ember.backend.dto.PendingFriendRequest
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.FriendService
import com.ember.backend.service.RateLimiterService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/friends")
class FriendsController(
    private val friendService: FriendService,
    private val rateLimiterService: RateLimiterService,
) {

    @GetMapping
    fun listFriends(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam(defaultValue = "false") refresh: Boolean,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "30") limit: Int,
    ): Page<FriendSummary> = friendService.getFriends(me.id, offset = offset, limit = limit, forceRefresh = refresh)

    @GetMapping("/pending")
    fun listPendingRequests(@AuthenticationPrincipal me: AuthenticatedUser): List<PendingFriendRequest> =
        friendService.getPendingRequests(me.id)

    @GetMapping("/search")
    fun searchUsers(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam q: String,
    ): List<FriendSearchResult> = friendService.searchUsers(me.id, q)

    @PostMapping("/request")
    fun sendRequest(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody request: FriendRequestRequest,
    ): PendingFriendRequest {
        // Sending a request by email returns a distinct 404 when no account matches — a
        // no-cost-limited oracle an account could otherwise use to mass-check whether specific
        // email addresses have an Ember account at all.
        rateLimiterService.checkLimit("friend-request:${me.id}", maxAttempts = 20, window = Duration.ofMinutes(15))
        return friendService.sendFriendRequest(me.id, request)
    }

    @PostMapping("/accept")
    fun acceptRequest(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody request: FriendAcceptRequest,
    ): FriendSummary = friendService.acceptFriendRequest(me.id, request.friendshipId)

    @PostMapping("/{friendshipId}/pin")
    fun pinFriend(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable friendshipId: UUID,
    ): FriendSummary = friendService.setPinned(me.id, friendshipId, pinned = true)

    @DeleteMapping("/{friendshipId}/pin")
    fun unpinFriend(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable friendshipId: UUID,
    ): FriendSummary = friendService.setPinned(me.id, friendshipId, pinned = false)

    @PostMapping("/{friendshipId}/streak/restore")
    fun restoreStreak(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable friendshipId: UUID,
    ): FriendSummary = friendService.restoreStreak(me.id, friendshipId)

    @DeleteMapping("/{friendshipId}")
    fun removeFriend(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable friendshipId: UUID,
    ) {
        friendService.removeFriend(me.id, friendshipId)
    }
}
