package com.ember.backend.service

import com.ember.backend.dto.BlockedUserSummary
import com.ember.backend.exception.InvalidSafetyActionException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.BlockedUser
import com.ember.backend.repository.BlockedUserRepository
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.UserRepository
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BlockService(
    private val blockedUserRepository: BlockedUserRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val r2StorageService: R2StorageService,
    private val cacheManager: CacheManager,
) {

    /** Removes any existing friendship or pending request between the two (either direction) —
     * blocking someone you're still friends with, or who still has a pending request with you,
     * shouldn't leave that relationship dangling. Enforced everywhere the two could otherwise
     * find or interact with each other: search (FriendService.searchUsers, via
     * BlockedUserRepository.existsBetween in the query itself) and sending a friend request
     * (FriendService.sendFriendRequest). Sending a photo needs no separate check — it's already
     * gated on an accepted friendship existing (PhotoService.upload), which this removes.
     *
     * A no-op if already blocked, so a doubled tap/request is harmless rather than a 409. */
    @Transactional
    fun block(blockerId: UUID, blockedId: UUID) {
        if (blockerId == blockedId) throw InvalidSafetyActionException("You can't block yourself")
        if (blockedUserRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId)) return

        val blocker = userRepository.findById(blockerId).orElseThrow { ResourceNotFoundException("User not found") }
        val blocked = userRepository.findById(blockedId).orElseThrow { ResourceNotFoundException("User not found") }
        blockedUserRepository.save(BlockedUser(blocker = blocker, blocked = blocked))

        friendshipRepository.findBetween(blockerId, blockedId)?.let { friendshipRepository.delete(it) }

        evictCaches(blockerId, blockedId)
    }

    /** Unblocking never restores whatever friendship/request existed before — that was deleted
     * outright when the block was created, not archived, so this only ever removes the block
     * record itself. If they want to reconnect, that's a fresh friend request like any stranger. */
    @Transactional
    fun unblock(blockerId: UUID, blockedId: UUID) {
        val existing = blockedUserRepository.findByBlocker_IdAndBlocked_Id(blockerId, blockedId) ?: return
        blockedUserRepository.delete(existing)
    }

    fun getBlockedUsers(blockerId: UUID): List<BlockedUserSummary> =
        blockedUserRepository.findAllByBlocker_IdOrderByCreatedAtDesc(blockerId).map {
            BlockedUserSummary(
                userId = it.blocked.id,
                displayName = it.blocked.displayName,
                username = it.blocked.username,
                profilePhotoUrl = it.blocked.profilePhotoStorageKey?.let { key -> r2StorageService.publicUrl(key) },
                blockedAt = it.createdAt,
            )
        }

    /** Same reasoning as FriendService.removeFriend's own cache eviction — a friendship that just
     * got deleted here must not keep surfacing stale friends/feed/activity data for either side
     * until the TTL happens to catch up on its own. */
    private fun evictCaches(userIdA: UUID, userIdB: UUID) {
        listOf("friends", "feed", "activity").forEach { cacheName ->
            cacheManager.getCache(cacheName)?.let { cache ->
                cache.evict(userIdA.toString())
                cache.evict(userIdB.toString())
            }
        }
    }
}
