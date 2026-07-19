package com.ember.backend.controller

import com.ember.backend.dto.FriendAcceptRequest
import com.ember.backend.dto.FriendRequestRequest
import com.ember.backend.dto.FriendSearchResult
import com.ember.backend.dto.FriendSummary
import com.ember.backend.dto.PendingFriendRequest
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.FriendService
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
import java.util.UUID

@RestController
@RequestMapping("/friends")
class FriendsController(private val friendService: FriendService) {

    @GetMapping
    fun listFriends(@AuthenticationPrincipal me: AuthenticatedUser): List<FriendSummary> =
        friendService.getFriends(me.id)

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
    ): PendingFriendRequest = friendService.sendFriendRequest(me.id, request)

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

    @DeleteMapping("/{friendshipId}")
    fun removeFriend(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable friendshipId: UUID,
    ) {
        friendService.removeFriend(me.id, friendshipId)
    }
}
