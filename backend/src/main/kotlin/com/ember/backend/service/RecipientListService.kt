package com.ember.backend.service

import com.ember.backend.dto.CreateRecipientListRequest
import com.ember.backend.dto.RecipientListSummary
import com.ember.backend.exception.InvalidRecipientListException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.FriendshipStatus
import com.ember.backend.model.RecipientList
import com.ember.backend.repository.FriendshipRepository
import com.ember.backend.repository.RecipientListRepository
import com.ember.backend.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import java.util.UUID

/** Backs the camera recipient picker's own saved-list shortcuts ("+" badge) — kept on the server
 * (not just on-device) specifically so a list created on one phone shows up on every other device
 * signed into the same account, the same way the friends list itself already does. */
@Service
class RecipientListService(
    private val recipientListRepository: RecipientListRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    fun getLists(ownerId: UUID): List<RecipientListSummary> =
        recipientListRepository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).map { it.toSummary() }

    /** [request.friendIds] is trusted for *shape* (a list of UUIDs) but not for *content* — it's
     * always narrowed down to whichever of those are actually accepted friends of [ownerId]
     * before saving, the same way PhotoService validates recipients before a send. A client bug
     * or a stale/tampered request could otherwise let an arbitrary UUID sit in a saved list
     * forever with no way for it to ever resolve to a real, displayable person. */
    fun createList(ownerId: UUID, request: CreateRecipientListRequest): RecipientListSummary {
        val requestedIds = request.friendIds.distinct()
        val actualFriendIds = friendshipRepository
            .findAllWithStatusBetween(ownerId, requestedIds, FriendshipStatus.ACCEPTED)
            .map { if (it.requester.id == ownerId) it.addressee.id else it.requester.id }
            .toSet()
        val validFriendIds = requestedIds.filter { it in actualFriendIds }
        if (validFriendIds.isEmpty()) {
            throw InvalidRecipientListException("Couldn't save that list — none of those are your friends")
        }

        val owner = userRepository.getReferenceById(ownerId)
        val list = RecipientList(
            owner = owner,
            name = request.name.trim(),
            friendIdsJson = objectMapper.writeValueAsString(validFriendIds),
        )
        return recipientListRepository.save(list).toSummary()
    }

    fun deleteList(ownerId: UUID, listId: UUID) {
        val deleted = recipientListRepository.deleteByIdAndOwnerId(listId, ownerId)
        if (deleted == 0L) {
            throw ResourceNotFoundException("List not found")
        }
    }

    private fun RecipientList.toSummary() = RecipientListSummary(
        id = id,
        name = name,
        friendIds = objectMapper.readValue<List<UUID>>(friendIdsJson),
        createdAt = createdAt,
    )
}
