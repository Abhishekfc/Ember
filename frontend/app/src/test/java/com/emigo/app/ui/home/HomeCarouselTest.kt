package com.emigo.app.ui.home

import com.emigo.app.data.remote.dto.FeedItem
import com.emigo.app.data.remote.dto.PhotoEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCarouselTest {

    private fun photo(id: String) = PhotoEntryDto(
        photoId = id,
        photoUrl = "https://photos.example/$id.jpg",
        createdAt = "2026-01-01T00:00:00Z",
        seen = false,
    )

    // The backend sends each friend's photos oldest first.
    private fun friend(id: String, name: String, streak: Int, vararg photoIds: String) =
        FeedItem(friendId = id, displayName = name, photos = photoIds.map { photo(it) }, streak = streak)

    @Test
    fun anEmptyFeedHasNoPages() {
        assertTrue(buildHomeCarousel(emptyList()).isEmpty())
    }

    @Test
    fun aFriendsPhotosAreShownNewestFirst() {
        val entries = buildHomeCarousel(listOf(friend("f1", "Ava", 2, "old", "mid", "new")))
        assertEquals(listOf("new", "mid", "old"), entries.map { it.photo.photoId })
    }

    @Test
    fun onlyTheNewestPhotoIsMarkedAsTheFriendsNewest() {
        val entries = buildHomeCarousel(listOf(friend("f1", "Ava", 2, "old", "mid", "new")))
        assertEquals(listOf(true, false, false), entries.map { it.isFriendsNewest })
    }

    @Test
    fun positionAndCountDriveThePhotoDots() {
        val entries = buildHomeCarousel(listOf(friend("f1", "Ava", 2, "a", "b", "c")))
        assertEquals(listOf(0, 1, 2), entries.map { it.indexWithinFriend })
        assertTrue(entries.all { it.totalForFriend == 3 })
    }

    @Test
    fun friendsKeepTheirFeedOrderAndTheirOwnDetails() {
        val entries = buildHomeCarousel(
            listOf(
                friend("f1", "Ava", 5, "a1", "a2"),
                friend("f2", "Ben", 0, "b1"),
            ),
        )
        assertEquals(listOf("f1", "f1", "f2"), entries.map { it.friendId })
        assertEquals(listOf("Ava", "Ava", "Ben"), entries.map { it.displayName })
        assertEquals(listOf(5, 5, 0), entries.map { it.streak })
        assertEquals(listOf(2, 2, 1), entries.map { it.totalForFriend })
    }

    @Test
    fun aFriendWithOnePhotoIsBothFirstAndLast() {
        val entries = buildHomeCarousel(listOf(friend("f1", "Ava", 0, "only")))
        assertEquals(1, entries.size)
        assertTrue(entries.single().isFriendsNewest)
        assertEquals(1, entries.single().totalForFriend)
        assertEquals(0, entries.single().indexWithinFriend)
    }
}
