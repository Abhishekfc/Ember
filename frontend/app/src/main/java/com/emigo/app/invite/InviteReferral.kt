package com.emigo.app.invite

import com.emigo.app.data.remote.dto.FriendSearchResultDto

/** The person whose invite link this install came from, as found on Emigo. */
data class Inviter(val userId: String, val username: String, val displayName: String)

/**
 * Turns "this install came from @ann's invite link" into a one-tap "Add @ann?".
 *
 * Google Play hands the app the link's `referrer` after an install (see [InstallReferrerReader]);
 * the invite page puts `invite=<username>` in it. This reads it once, remembers the name until it
 * has been dealt with, and looks the person up. It is deliberately quiet: any problem (no invite,
 * no network, no such person, already friends) just means nothing is offered, never an error. A
 * lookup that failed on the network keeps the name for the next launch.
 */
class InviteReferral(
    private val store: InviteStore,
    private val readInstallReferrer: suspend () -> Result<String?>,
    private val search: suspend (query: String) -> Result<List<FriendSearchResultDto>>,
    private val sendRequest: suspend (userId: String) -> Result<Unit>,
) {
    /** Who to offer to add, or null when there is nobody. */
    suspend fun inviterToOffer(): Inviter? {
        if (!store.hasCheckedReferrer()) {
            readInstallReferrer().onSuccess { referrer ->
                store.markReferrerChecked()
                usernameFromReferrer(referrer)?.let { store.setPendingInviter(it) }
            }
            // A failure (Play not reachable right now) leaves it unchecked, to try again later.
        }

        val username = store.pendingInviter() ?: return null
        // Keeps the name when the lookup itself failed (offline): it is tried again next time.
        val matches = search(username).getOrElse { return null }
        val match = matches.firstOrNull { it.username.equals(username, ignoreCase = true) }
        val canOffer = match != null &&
            !match.requested &&
            match.friendshipId == null &&
            !match.isPendingFromThem
        if (!canOffer) {
            // No such person, or already friends, or a request is already on its way: nothing to offer.
            store.setPendingInviter(null)
            return null
        }
        return Inviter(userId = match!!.userId, username = match.username, displayName = match.displayName)
    }

    /** Sends the friend request. Forgets the invite once it has gone through. */
    suspend fun accept(inviter: Inviter): Result<Unit> =
        sendRequest(inviter.userId).onSuccess { store.setPendingInviter(null) }

    /** "Maybe later": the invite is forgotten, so it doesn't come back at every launch. */
    suspend fun dismiss() = store.setPendingInviter(null)
}
