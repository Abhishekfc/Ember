package com.ember.backend.service

import com.ember.backend.dto.FriendRequestRequest
import com.ember.backend.dto.FriendSearchResult
import com.ember.backend.dto.FriendSummary
import com.ember.backend.dto.Page
import com.ember.backend.dto.PendingFriendRequest
import com.ember.backend.exception.GoldSubscriptionRequiredException
import com.ember.backend.exception.InvalidFriendRequestException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.exception.StreakRestoreNotAvailableException
import com.ember.backend.model.Friendship
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.model.User
import com.ember.backend.repository.BlockedUserRepository
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.FriendshipStreakStateRepository
import com.ember.backend.repository.PhotoRecipientRepository
import com.ember.backend.repository.UserRepository
import com.ember.backend.repository.existsBetween
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private const val SEARCH_RESULT_LIMIT = 20

/** Ceiling on a single page, since `limit` comes straight off the query string — see the clamp in
 * [FriendService.getFriends]. Comfortably above the client's own default of 30. */
private const val MAX_PAGE_SIZE = 100

/** Ceiling on a search string's length. Longer than any real name, and short enough that the
 * trigram index is never asked to match a pathologically long pattern. */
private const val MAX_SEARCH_QUERY_LENGTH = 100

@Service
class FriendService(
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
    private val blockedUserRepository: BlockedUserRepository,
    private val r2StorageService: R2StorageService,
    private val cacheManager: CacheManager,
    private val pushNotificationService: PushNotificationService,
    private val friendshipStreakStateRepository: FriendshipStreakStateRepository,
    private val subscriptionService: SubscriptionService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Cached per-user — hit on every Friends screen open, and streak/pin state here changes
     * far less often than it's read. Evicted for both sides of a friendship on accept/pin/remove
     * (below), and for both sides of a photo exchange in PhotoService.upload.
     *
     * [forceRefresh] (set from the Friends screen's pull-to-refresh gesture) skips the cache
     * *read* but still writes the fresh result back — see [PhotoService.getFeed] for why this
     * needs to be done by hand rather than through a plain `@Cacheable` annotation. */
    fun getFriends(userId: UUID, offset: Int = 0, limit: Int = 30, forceRefresh: Boolean = false): Page<FriendSummary> {
        val cache = cacheManager.getCache("friends")
        val cacheKey = userId.toString()
        val summaries = if (!forceRefresh) {
            // See PhotoService.getFeed: a cache read failure (e.g. an empty-list result that
            // GenericJackson2JsonRedisSerializer doesn't round-trip reliably) must be treated as
            // a miss, not allowed to fail the request.
            runCatching { cache?.get(cacheKey, List::class.java) }.getOrNull()?.let {
                @Suppress("UNCHECKED_CAST")
                it as List<FriendSummary>
            } ?: computeFriendSummaries(userId).also { cache?.put(cacheKey, it) }
        } else {
            computeFriendSummaries(userId).also { cache?.put(cacheKey, it) }
        }

        // Paginated in memory rather than at the query level — this whole list is already
        // fetched (and cached) as one unit above regardless of which page is requested, since
        // streak/pin computation needs every friendship anyway; slicing it here means the
        // *response* stays small without a separate DB-level pagination path for what's a
        // per-user list that's realistically never huge.
        //
        // Both values are clamped because they arrive straight off the query string: `drop`/`take`
        // both `require(n >= 0)`, so a plain `?offset=-1` threw IllegalArgumentException out of
        // here and surfaced as a 500, and an unbounded `limit` let one request ask for the entire
        // list in a single response.
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        val page = summaries.drop(safeOffset).take(safeLimit)
        return Page(items = page, hasMore = safeOffset + safeLimit < summaries.size)
    }

    private fun computeFriendSummaries(userId: UUID): List<FriendSummary> {
        val friendships = friendshipRepository.findAllForUserWithStatus(userId, FriendshipStatus.ACCEPTED)
        if (friendships.isEmpty()) return emptyList()

        val friendIds = friendships.map { if (it.requester.id == userId) it.addressee.id else it.requester.id }
        // One query for every friend's exchange history instead of one query per friend — this
        // used to be the single biggest N+1 in the app, since it ran on every cache-miss load of
        // the Friends tab for every friend the user has.
        val timestampsByFriend = photoRecipientRepository.findExchangeTimestampsBatch(userId, friendIds)
            .groupBy({ it.otherPartyId }, { StreakExchange(it.createdAt, it.sentByMe) })

        // Same batching reasoning as the exchange-history query above — one lookup for every
        // friendship's streak-restore state instead of one per friendship in the loop below.
        val streakStateByFriendshipId = friendshipStreakStateRepository.findAllById(friendships.map { it.id })
            .associateBy { it.friendshipId }

        return friendships.map { friendship ->
            val isRequester = friendship.requester.id == userId
            val friend = if (isRequester) friendship.addressee else friendship.requester
            val exchanges = timestampsByFriend[friend.id] ?: emptyList()
            val lastExchange = exchanges.maxByOrNull { it.timestamp }
            val streakState = streakStateByFriendshipId[friendship.id]

            val streak = StreakCalculator.compute(exchanges, streakState?.restoredThroughDate)
            // Raw deadlines, not precomputed booleans — see FriendSummary's own doc comment on
            // streakDeadlineEpochSeconds for why: a boolean is only ever correct at the instant
            // it's computed, which breaks down the moment this response gets cached and viewed
            // offline sometime later. The client re-evaluates both against its own current clock
            // on every render instead.
            val mostRecentMutualDay = StreakCalculator.mostRecentMutualDay(exchanges, streakState?.restoredThroughDate)
            val streakDeadline = StreakCalculator.currentWindowDeadline(streak, mostRecentMutualDay)
            // Only while a break is genuinely still open: a deadline exists, and this break
            // hasn't already been restored (restoredThroughDate would be set). Whether that
            // deadline has *passed* is left to the client's own clock, same as streakDeadline.
            val streakRestoreDeadline = streakState
                ?.takeIf { it.restoredThroughDate == null }
                ?.restoreDeadline

            FriendSummary(
                friendshipId = friendship.id,
                friendId = friend.id,
                displayName = friend.displayName,
                username = friend.username,
                profilePhotoUrl = friend.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
                pinnedByMe = if (isRequester) friendship.requesterPinned else friendship.addresseePinned,
                pinnedByThem = if (isRequester) friendship.addresseePinned else friendship.requesterPinned,
                // "Last sent" stays about either direction — it's just informational, not the
                // streak's own mutual-day rule.
                lastActivityAt = lastExchange?.timestamp,
                // Who that most recent exchange was actually from — lets the client say "You
                // sent" vs "Sent to you" instead of a direction-blind "Last sent", without
                // needing its own copy of the exchange list just to work that out.
                lastActivityBySelf = lastExchange?.sentByMe,
                streak = streak,
                streakDeadlineEpochSeconds = streakDeadline?.epochSecond,
                streakRestoreDeadlineEpochSeconds = streakRestoreDeadline?.epochSecond,
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
                    profilePhotoUrl = it.requester.profilePhotoStorageKey?.let { key -> r2StorageService.publicUrl(key) },
                    createdAt = it.createdAt,
                )
            }

    fun searchUsers(userId: UUID, query: String): List<FriendSearchResult> {
        val trimmed = query.trim().take(MAX_SEARCH_QUERY_LENGTH)
        if (trimmed.length < 2) return emptyList()

        // The repository query interpolates this into a LIKE pattern, so `%` and `_` were being
        // taken as wildcards rather than as literal characters someone typed. That made `%%` (or
        // any two characters including one) match *every* account in the system — turning a
        // name search into a user-enumeration endpoint, and defeating the two-character minimum
        // above that exists precisely to stop broad matches. Escaping them makes the search mean
        // what it looks like it means: a literal substring match.
        val escaped = trimmed.escapeLikeWildcards()
        val results = userRepository.search(userId, escaped, PageRequest.of(0, SEARCH_RESULT_LIMIT))
        if (results.isEmpty()) return emptyList()

        // One query covering every result instead of one findBetween call per result.
        val resultIds = results.map { it.id }
        val friendshipsByOtherUserId = friendshipRepository.findAllBetween(userId, resultIds)
            .associateBy { if (it.requester.id == userId) it.addressee.id else it.requester.id }

        return results.map { user ->
            val friendship = friendshipsByOtherUserId[user.id]
            FriendSearchResult(
                userId = user.id,
                displayName = user.displayName,
                username = user.username,
                requested = friendship != null,
                friendshipId = friendship?.id,
                isPendingFromMe = friendship != null &&
                    friendship.status == FriendshipStatus.PENDING &&
                    friendship.requester.id == userId,
                isPendingFromThem = friendship != null &&
                    friendship.status == FriendshipStatus.PENDING &&
                    friendship.requester.id != userId,
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
        // Defense-in-depth — search already excludes blocked pairs (UserRepository.search), so
        // this path is normally unreachable through the UI, but a request naming a userId
        // directly (a stale client cache, a direct API call) must still be rejected server-side.
        if (blockedUserRepository.existsBetween(requester.id, addressee.id)) {
            throw InvalidFriendRequestException("You can't send a friend request to this person")
        }

        val friendship = friendshipRepository.save(
            Friendship(requester = requester, addressee = addressee)
        )
        logger.info(
            "Friend request sent: from userId={} ({}) to userId={} ({})",
            requester.id, requester.email, addressee.id, addressee.email,
        )
        // Only the addressee sees a REQUEST_INCOMING event for this.
        cacheManager.getCache("activity")?.evict(addressee.id.toString())
        pushNotificationService.notifyFriendRequestReceived(requester.displayName, addressee.id)
        return PendingFriendRequest(
            friendshipId = friendship.id,
            requesterId = requester.id,
            displayName = requester.displayName,
            username = requester.username,
            profilePhotoUrl = requester.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
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
        evictFriendsCache(friendship.requester.id, friendship.addressee.id)
        // Only the original requester gets a REQUEST_ACCEPTED event for this.
        cacheManager.getCache("activity")?.evict(friendship.requester.id.toString())
        pushNotificationService.notifyFriendRequestAccepted(friendship.addressee.displayName, friendship.requester.id)

        return FriendSummary(
            friendshipId = friendship.id,
            friendId = friendship.requester.id,
            displayName = friendship.requester.displayName,
            username = friendship.requester.username,
            profilePhotoUrl = friendship.requester.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
            pinnedByMe = friendship.addresseePinned,
            pinnedByThem = friendship.requesterPinned,
            lastActivityAt = null,
            lastActivityBySelf = null,
            streak = 0,
            streakDeadlineEpochSeconds = null,
            streakRestoreDeadlineEpochSeconds = null,
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
        evictFriendsCache(friendship.requester.id, friendship.addressee.id)

        val friend: User = if (isRequester) friendship.addressee else friendship.requester
        return FriendSummary(
            friendshipId = friendship.id,
            friendId = friend.id,
            displayName = friend.displayName,
            username = friend.username,
            profilePhotoUrl = friend.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
            pinnedByMe = if (isRequester) friendship.requesterPinned else friendship.addresseePinned,
            pinnedByThem = if (isRequester) friendship.addresseePinned else friendship.requesterPinned,
            lastActivityAt = null,
            lastActivityBySelf = null,
            streak = 0,
            streakDeadlineEpochSeconds = null,
            streakRestoreDeadlineEpochSeconds = null,
        )
    }

    /** Restores exactly the one day [StreakBreakDetectionService] flagged as missed when this
     * friendship's streak broke — never touches real exchange history (see
     * [StreakCalculator.compute]'s own doc comment on why), just tells the calculator to treat
     * that one day as covered from now on. Gold-gated server-side, not just hidden client-side —
     * a paywalled action must never trust the caller's own claim about their subscription status. */
    @Transactional
    fun restoreStreak(userId: UUID, friendshipId: UUID): FriendSummary {
        val friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow { ResourceNotFoundException("Friendship not found") }

        val isRequester = friendship.requester.id == userId
        val isAddressee = friendship.addressee.id == userId
        if (!isRequester && !isAddressee) {
            throw InvalidFriendRequestException("You are not part of this friendship")
        }
        if (friendship.status != FriendshipStatus.ACCEPTED) {
            throw InvalidFriendRequestException("You can only restore a streak with an accepted friend")
        }
        if (!subscriptionService.isActiveGoldMember(userId)) {
            throw GoldSubscriptionRequiredException()
        }

        val state = friendshipStreakStateRepository.findById(friendshipId)
            .orElseThrow { StreakRestoreNotAvailableException() }
        val deadline = state.restoreDeadline
        if (deadline == null || deadline.isBefore(Instant.now()) || state.restoredThroughDate != null) {
            throw StreakRestoreNotAvailableException()
        }

        // Bridged through *yesterday*, not through the single day that originally lapsed: the
        // restore window is two days wide (StreakBreakDetectionService.STREAK_RESTORE_WINDOW_DAYS),
        // so a restore bought on its second day has two missed days behind it, and reconnecting
        // only the first would leave the chain still broken — a paid action returning a streak of
        // zero. StreakCalculator.compute walks back from this date and fills exactly the gap it
        // finds, so naming yesterday restores the chain right up to today whether that gap is one
        // day or two. Never today: the point of a restore is to put the streak back in play, not
        // to also cover the exchange still owed today to keep it alive.
        state.restoredThroughDate = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        state.updatedAt = Instant.now()
        friendshipStreakStateRepository.save(state)
        evictFriendsCache(friendship.requester.id, friendship.addressee.id)
        // The Activity feed carries a STREAK_BROKEN row driven by this exact state (see
        // ActivityService) — without evicting it too, the row someone just paid to resolve keeps
        // showing until the 30s TTL happens to lapse. Same both-sides eviction removeFriend does.
        cacheManager.getCache("activity")?.let { cache ->
            cache.evict(friendship.requester.id.toString())
            cache.evict(friendship.addressee.id.toString())
        }
        logger.info("Streak restored: friendshipId={} by userId={}", friendshipId, userId)

        val friend: User = if (isRequester) friendship.addressee else friendship.requester
        val exchanges = photoRecipientRepository.findExchangeTimestampsBatch(userId, listOf(friend.id))
            .map { StreakExchange(it.createdAt, it.sentByMe) }
        val restoredStreak = StreakCalculator.compute(exchanges, state.restoredThroughDate)
        // Same fields, computed the same way computeFriendSummaries computes them for every other
        // friendship — this response replaces that friend's row in-place on the client
        // (FriendsViewModel.applyUpdatedFriend), so hardcoding these to null (as this used to do)
        // wiped out real history the client already had: the row briefly showed "No photos yet"
        // and sorted as if it had never had any activity, since the Friends list orders by
        // lastActivityAt descending.
        val lastExchange = exchanges.maxByOrNull { it.timestamp }
        val mostRecentMutualDay = StreakCalculator.mostRecentMutualDay(exchanges, state.restoredThroughDate)
        val streakDeadline = StreakCalculator.currentWindowDeadline(restoredStreak, mostRecentMutualDay)

        return FriendSummary(
            friendshipId = friendship.id,
            friendId = friend.id,
            displayName = friend.displayName,
            username = friend.username,
            profilePhotoUrl = friend.profilePhotoStorageKey?.let { r2StorageService.publicUrl(it) },
            pinnedByMe = if (isRequester) friendship.requesterPinned else friendship.addresseePinned,
            pinnedByThem = if (isRequester) friendship.addresseePinned else friendship.requesterPinned,
            lastActivityAt = lastExchange?.timestamp,
            lastActivityBySelf = lastExchange?.sentByMe,
            streak = restoredStreak,
            streakDeadlineEpochSeconds = streakDeadline?.epochSecond,
            // Just restored — restoredThroughDate is non-null on state now, so this is correctly
            // null (no live restore window open any more), matching the same
            // ?.takeIf { restoredThroughDate == null } gate computeFriendSummaries applies.
            streakRestoreDeadlineEpochSeconds = null,
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
        // Also evicts feed, not just friends — a removed friend's existing photos should stop
        // showing up immediately rather than lingering until the TTL catches up.
        evictFriendsCache(friendship.requester.id, friendship.addressee.id)
        cacheManager.getCache("feed")?.let { cache ->
            cache.evict(friendship.requester.id.toString())
            cache.evict(friendship.addressee.id.toString())
        }
        // This same endpoint also covers declining/cancelling a still-PENDING request (no status
        // check above) — either way, whichever side had a REQUEST_INCOMING or STREAK_EXPIRING
        // entry for this friendship shouldn't keep seeing it.
        cacheManager.getCache("activity")?.let { cache ->
            cache.evict(friendship.requester.id.toString())
            cache.evict(friendship.addressee.id.toString())
        }
    }

    private fun evictFriendsCache(vararg userIds: UUID) {
        val cache = cacheManager.getCache("friends") ?: return
        userIds.forEach { cache.evict(it.toString()) }
    }
}

/** Neutralizes the two SQL LIKE metacharacters so a search string is matched literally — see
 * [FriendService.searchUsers]. Uses `!` as the escape character rather than the conventional
 * backslash purely to keep the JPQL literal unambiguous (`escape '!'` in
 * [com.ember.backend.repository.UserRepository.search] must stay in step with this); the escape
 * character itself has to be escaped first, or escaping `%` would produce a sequence this same
 * function could no longer read back. */
internal fun String.escapeLikeWildcards(): String =
    replace("!", "!!").replace("%", "!%").replace("_", "!_")
