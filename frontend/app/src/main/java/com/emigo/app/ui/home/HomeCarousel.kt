package com.emigo.app.ui.home

import com.emigo.app.data.remote.dto.FeedItem
import com.emigo.app.data.remote.dto.PhotoEntryDto

/** One page of the flattened carousel: a friend's photos newest first, then the next friend's.
 * [isFriendsNewest] marks a friend's newest photo. */
internal data class HomeCarouselEntry(
    val friendId: String,
    val displayName: String,
    val streak: Int,
    val photo: PhotoEntryDto,
    val isFriendsNewest: Boolean,
    /** Position within this friend's photos (0 = newest) and how many they have; drives the
     * photo-count dots. */
    val indexWithinFriend: Int,
    val totalForFriend: Int,
)

internal fun buildHomeCarousel(feedItems: List<FeedItem>): List<HomeCarouselEntry> {
    val entries = mutableListOf<HomeCarouselEntry>()
    feedItems.forEach { item ->
        val ordered = item.photos.asReversed()
        ordered.forEachIndexed { index, photo ->
            entries += HomeCarouselEntry(
                friendId = item.friendId,
                displayName = item.displayName,
                streak = item.streak,
                photo = photo,
                isFriendsNewest = index == 0,
                indexWithinFriend = index,
                totalForFriend = ordered.size,
            )
        }
    }
    return entries
}

/** Where a given friend's (remembered) position lands in the flattened carousel — used both to
 * pick the pager's start page and to jump there when an avatar is tapped directly. */
internal fun pageIndexFor(
    entries: List<HomeCarouselEntry>,
    feedItems: List<FeedItem>,
    friendId: String?,
    viewModel: HomeViewModel,
): Int {
    if (entries.isEmpty()) return 0
    val targetFriendId = friendId ?: feedItems.firstOrNull()?.friendId ?: return 0
    var pagesBefore = 0
    for (item in feedItems) {
        if (item.friendId == targetFriendId) {
            val within = viewModel.photoIndexFor(targetFriendId).coerceIn(0, (item.photos.size - 1).coerceAtLeast(0))
            return (pagesBefore + within).coerceIn(0, entries.lastIndex)
        }
        pagesBefore += item.photos.size
    }
    return 0
}
