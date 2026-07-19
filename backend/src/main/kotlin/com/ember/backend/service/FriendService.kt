package com.ember.backend.service

import com.ember.backend.dto.FriendRequestRequest
import com.ember.backend.dto.FriendSearchResult
import com.ember.backend.dto.FriendSummary
import com.ember.backend.dto.PendingFriendRequest
import com.ember.backend.exception.InvalidFriendRequestException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.Friendship
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.model.User
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.PhotoRecipientRepository
import com.ember.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private const val SEARCH_RESULT_LIMIT = 20

@Service
class FriendService(
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    private val r2StorageService: R2StorageService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getFriends(userId: UUID): List<FriendSummary> {
        val friendships = friendshipRepository.findAllForUserWithStatus(userId, FriendshipStatus.ACCEPTED)

        return friendships.map { friendship ->
            val isRequester = friendship.requester.id == userId
            val friend = if (isRequester) friendship.addressee else friendship.requester
            val exchangeTimestamps = photoRecipientRepository.findExchangeTimestamps(userId, friend.id)

            FriendSummary(
                friendshipId = friendship.id,
                friendId = friend.id,
                displayName = friend.displayName,
                username = friend.username,
                email = friend.email,
                profilePhotoUrl = friend.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
                pinnedByMe = if (isRequester) friendship.requesterPinned else friendship.addresseePinned,
                pinnedByThem = if (isRequester) friendship.addresseePinned else friendship.requesterPinned,
                lastActivityAt = exchangeTimestamps.maxOrNull(),
                streak = StreakCalculator.compute(exchangeTimestamps),
            )
        }
    }

    fun getPendingRequests(userId: UUID): List<PendingFriendRequest> =
        friendshipRepository.findAllForUserWithStatus(userId, FriendshipStatus.PENDING)
            .filter { it.addressee.id == userId }
            .map {
                PendingFriendRequest(
                    friendshipId = it.id,
                    requesterId = it.requester.id,
                    displayName = it.requester.displayName,
                    username = it.requester.username,
                    email = it.requester.email,
                    createdAt = it.createdAt,
                )
            }

    fun searchUsers(userId: UUID, query: String): List<FriendSearchResult> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        return userRepository.search(userId, trimmed, PageRequest.of(0, SEARCH_RESULT_LIMIT)).map { user ->
            FriendSearchResult(
                userId = user.id,
                displayName = user.displayName,
                username = user.username,
                requested = friendshipRepository.findBetween(userId, user.id) != null,
            )
        }
    }

    @Transactional
    fun sendFriendRequest(userId: UUID, request: FriendRequestRequest): PendingFriendRequest {
        val requester = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val addressee = when {
            request.targetUserId != null -> userRepository.findById(request.targetUserId)
                .orElseThrow { ResourceNotFoundException("User not found") }
            !request.email.isNullOrBlank() -> userRepository.findByEmail(request.email.trim().lowercase())
                ?: throw ResourceNotFoundException("No user found with that email")
            else -> throw InvalidFriendRequestException("Either targetUserId or email is required")
        }

        if (addressee.id == requester.id) {
            throw InvalidFriendRequestException("You cannot send a friend request to yourself")
        }
        if (friendshipRepository.findBetween(requester.id, addressee.id) != null) {
            throw InvalidFriendRequestException("A friendship or pending request already exists")
        }

        val friendship = friendshipRepository.save(
            Friendship(requester = requester, addressee = addressee)
        )
        logger.info(
            "Friend request sent: from userId={} ({}) to userId={} ({})",
            requester.id, requester.email, addressee.id, addressee.email,
        )
        return PendingFriendRequest(
            friendshipId = friendship.id,
            requesterId = requester.id,
            displayName = requester.displayName,
            username = requester.username,
            email = requester.email,
            createdAt = friendship.createdAt,
        )
    }

    @Transactional
    fun acceptFriendRequest(userId: UUID, friendshipId: UUID): FriendSummary {
        val friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow { ResourceNotFoundException("Friend request not found") }

        if (friendship.addressee.id != userId) {
            throw InvalidFriendRequestException("Only the recipient of a request can accept it")
        }
        if (friendship.status != FriendshipStatus.PENDING) {
            throw InvalidFriendRequestException("This friend request is no longer pending")
        }

        friendship.status = FriendshipStatus.ACCEPTED
        friendship.respondedAt = Instant.now()
        friendshipRepository.save(friendship)
        logger.info(
            "Friend request accepted: userId={} ({}) and userId={} ({}) are now friends",
            friendship.requester.id, friendship.requester.email, friendship.addressee.id, friendship.addressee.email,
        )

        return FriendSummary(
            friendshipId = friendship.id,
            friendId = friendship.requester.id,
            displayName = friendship.requester.displayName,
            username = friendship.requester.username,
            email = friendship.requester.email,
            profilePhotoUrl = friendship.requester.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
            pinnedByMe = friendship.addresseePinned,
            pinnedByThem = friendship.requesterPinned,
            lastActivityAt = null,
            streak = 0,
        )
    }

    @Transactional
    fun setPinned(userId: UUID, friendshipId: UUID, pinned: Boolean): FriendSummary {
        val friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow { ResourceNotFoundException("Friendship not found") }

        val isRequester = friendship.requester.id == userId
        val isAddressee = friendship.addressee.id == userId
        if (!isRequester && !isAddressee) {
            throw InvalidFriendRequestException("You are not part of this friendship")
        }
        if (friendship.status != FriendshipStatus.ACCEPTED) {
            throw InvalidFriendRequestException("You can only pin an accepted friend")
        }

        if (isRequester) friendship.requesterPinned = pinned else friendship.addresseePinned = pinned
        friendshipRepository.save(friendship)

        val friend: User = if (isRequester) friendship.addressee else friendship.requester
        return FriendSummary(
            friendshipId = friendship.id,
            friendId = friend.id,
            displayName = friend.displayName,
            username = friend.username,
            email = friend.email,
            profilePhotoUrl = friend.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
            pinnedByMe = if (isRequester) friendship.requesterPinned else friendship.addresseePinned,
            pinnedByThem = if (isRequester) friendship.addresseePinned else friendship.requesterPinned,
            lastActivityAt = null,
            streak = 0,
        )
    }

    @Transactional
    fun removeFriend(userId: UUID, friendshipId: UUID) {
        val friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow { ResourceNotFoundException("Friendship not found") }

        val isRequester = friendship.requester.id == userId
        val isAddressee = friendship.addressee.id == userId
        if (!isRequester && !isAddressee) {
            throw InvalidFriendRequestException("You are not part of this friendship")
        }

        friendshipRepository.delete(friendship)
        logger.info(
            "Friendship removed: userId={} removed friendshipId={} (with userId={})",
            userId, friendship.id, if (isRequester) friendship.addressee.id else friendship.requester.id,
        )
    }
}
