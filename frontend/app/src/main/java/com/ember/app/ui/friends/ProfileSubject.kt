package com.ember.app.ui.friends

import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.PendingFriendRequestDto

/** Whoever [FriendProfileScreen] is currently showing — the same profile layout (avatar, name,
 * username) is used whether this is an already-accepted friend or someone with a pending
 * incoming request, differing only in which actions the screen offers at the bottom (Send
 * photo/Pin/Remove for a friend, Accept/Decline for a pending request). One screen per person,
 * not two near-duplicate ones — explicitly requested ("profile page should be of same design for
 * every person"), and the natural place further profile content (whatever that ends up being)
 * only ever needs to be built once. */
sealed interface ProfileSubject {
    val friendshipId: String
    val displayName: String
    val username: String
    val profilePhotoUrl: String?

    data class Friend(val summary: FriendSummaryDto) : ProfileSubject {
        override val friendshipId get() = summary.friendshipId
        override val displayName get() = summary.displayName
        override val username get() = summary.username
        override val profilePhotoUrl get() = summary.profilePhotoUrl
    }

    data class PendingRequest(val request: PendingFriendRequestDto) : ProfileSubject {
        override val friendshipId get() = request.friendshipId
        override val displayName get() = request.displayName
        override val username get() = request.username
        override val profilePhotoUrl get() = request.profilePhotoUrl
    }
}
