package com.emigo.app.ui.friends

import com.emigo.app.data.remote.dto.FriendSearchResultDto
import com.emigo.app.data.remote.dto.FriendSummaryDto
import com.emigo.app.data.remote.dto.PendingFriendRequestDto

/** Whoever [FriendProfileScreen] is currently showing — the same profile layout (avatar, name,
 * username) is used for an already-accepted friend, someone with a pending incoming request, or
 * a stranger found via search, differing only in which actions the screen offers at the bottom
 * (Send photo/Pin/Remove for a friend, Accept/Decline for a pending request, Add for a stranger).
 * One screen per person, not three near-duplicate ones — explicitly requested ("profile page
 * should be of same design for every person"), and the natural place further profile content
 * (whatever that ends up being) only ever needs to be built once.
 *
 * [friendshipId] is nullable because a freshly-found stranger has no friendship row yet — every
 * action that needs one ([FriendProfileViewModel.removeFriend]/`acceptRequest`/`rejectRequest`)
 * is only ever reachable once one exists, so those simply guard on it being present rather than
 * the type system ruling it out for those two cases specifically. [userId] is what's stable
 * across every case regardless of relationship state — the actual identity to key a ViewModel
 * on, since [friendshipId] can be null (or created out from under an already-open profile, once
 * [FriendProfileViewModel.sendRequest] succeeds) while [userId] never changes for the same
 * person. */
sealed interface ProfileSubject {
    val userId: String
    val friendshipId: String?
    val displayName: String
    val username: String
    val profilePhotoUrl: String?

    data class Friend(val summary: FriendSummaryDto) : ProfileSubject {
        override val userId get() = summary.friendId
        override val friendshipId get() = summary.friendshipId
        override val displayName get() = summary.displayName
        override val username get() = summary.username
        override val profilePhotoUrl get() = summary.profilePhotoUrl
    }

    data class PendingRequest(val request: PendingFriendRequestDto) : ProfileSubject {
        override val userId get() = request.requesterId
        override val friendshipId get() = request.friendshipId
        override val displayName get() = request.displayName
        override val username get() = request.username
        override val profilePhotoUrl get() = request.profilePhotoUrl
    }

    // Search results carry no photo URL at all today (FriendSearchResult doesn't fetch one) — see
    // FindPeopleScreen's own row for the same fallback-to-initial treatment this leans on.
    data class SearchResult(val result: FriendSearchResultDto) : ProfileSubject {
        override val userId get() = result.userId
        override val friendshipId get() = result.friendshipId
        override val displayName get() = result.displayName
        override val username get() = result.username
        override val profilePhotoUrl get() = null
    }
}
