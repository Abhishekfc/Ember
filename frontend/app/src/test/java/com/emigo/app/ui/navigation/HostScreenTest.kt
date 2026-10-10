package com.emigo.app.ui.navigation

import com.emigo.app.data.remote.dto.FriendSearchResultDto
import com.emigo.app.ui.friends.ProfileSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val someone: ProfileSubject = ProfileSubject.SearchResult(
    FriendSearchResultDto(userId = "u1", displayName = "Ann", username = "ann", requested = false),
)

private fun screenFor(
    nested: NestedScreen? = null,
    subject: ProfileSubject? = null,
    returnTo: NestedScreen? = null,
    picker: Boolean = false,
) = hostScreenFor(nested, subject, returnTo, picker)

/** Which page the host shows, and which way it slides. The mapping must match what the host's old
 * `when` chose, because only how pages arrive and leave changed, never which page is shown. */
class HostScreenTest {

    @Test
    fun withNothingOpenTheTabsShow() {
        assertEquals(HostScreen.Tabs, screenFor())
    }

    @Test
    fun everyNestedPageShowsItself() {
        val pages = NestedScreen.entries - setOf(NestedScreen.GOLD, NestedScreen.FRIEND_PROFILE)
        for (page in pages) assertEquals(HostScreen.Nested(page), screenFor(nested = page))
    }

    @Test
    fun theGoldPageIsAnOverlayNotAPageSoTheTabsStayUnderneath() {
        assertEquals(HostScreen.Tabs, screenFor(nested = NestedScreen.GOLD))
    }

    @Test
    fun theRecipientPickerShowsWhenNoNestedPageIsOpen() {
        assertEquals(HostScreen.Picker, screenFor(picker = true))
    }

    @Test
    fun aNestedPageWinsOverThePicker() {
        assertEquals(HostScreen.Nested(NestedScreen.FIND_PEOPLE), screenFor(nested = NestedScreen.FIND_PEOPLE, picker = true))
    }

    @Test
    fun aFriendProfileNeedsAPersonToShow() {
        assertEquals(HostScreen.Tabs, screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = null))
        assertTrue(screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = someone) is HostScreen.FriendProfile)
    }

    @Test
    fun aProfileKeepsItsPersonForTheSlideOut() {
        // The reason the host works from this value: the page that is leaving still has its person.
        val leaving = screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = someone) as HostScreen.FriendProfile

        assertEquals(someone, leaving.subject)
    }

    @Test
    fun depthsGoTabsThenPagesThenProfilesOpenedFromPages() {
        assertEquals(0, HostScreen.Tabs.depth)
        assertEquals(1, HostScreen.Picker.depth)
        assertEquals(1, HostScreen.Nested(NestedScreen.THEME).depth)
        assertEquals(1, screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = someone, returnTo = null).depth)
        assertEquals(1, screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = someone, returnTo = NestedScreen.PROFILE).depth)
        assertEquals(2, screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = someone, returnTo = NestedScreen.FIND_PEOPLE).depth)
        assertEquals(2, screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = someone, returnTo = NestedScreen.ACTIVITY).depth)
    }

    @Test
    fun openingAPageFromTheTabsPushes() {
        assertTrue(isPush(HostScreen.Tabs, HostScreen.Nested(NestedScreen.PROFILE)))
        assertTrue(isPush(HostScreen.Tabs, HostScreen.Picker))
    }

    @Test
    fun goingBackToTheTabsPops() {
        assertFalse(isPush(HostScreen.Nested(NestedScreen.PROFILE), HostScreen.Tabs))
        assertFalse(isPush(HostScreen.Picker, HostScreen.Tabs))
    }

    @Test
    fun openingAProfileFromFindFriendsPushesAndClosingItPops() {
        val findFriends = HostScreen.Nested(NestedScreen.FIND_PEOPLE)
        val profile = screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = someone, returnTo = NestedScreen.FIND_PEOPLE)

        assertTrue(isPush(findFriends, profile))
        assertFalse(isPush(profile, findFriends))
    }

    @Test
    fun aProfileOpenedFromTheFriendsTabPushesFromAndPopsToTheTabs() {
        val profile = screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = someone, returnTo = null)

        assertTrue(isPush(HostScreen.Tabs, profile))
        assertFalse(isPush(profile, HostScreen.Tabs))
    }

    @Test
    fun aProfileThatUpdatesInPlaceIsTheSamePage() {
        // A request accepted on the profile replaces the person but is not a new page: no slide.
        val before = screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = someone, returnTo = NestedScreen.FIND_PEOPLE)
        val other = ProfileSubject.SearchResult(FriendSearchResultDto(userId = "u1", displayName = "Ann", username = "ann", requested = true))
        val after = screenFor(nested = NestedScreen.FRIEND_PROFILE, subject = other, returnTo = NestedScreen.FIND_PEOPLE)

        assertNotEquals(before, after)
        assertEquals(before.key, after.key)
    }

    @Test
    fun differentPagesHaveDifferentKeys() {
        val keys = (NestedScreen.entries - setOf(NestedScreen.GOLD, NestedScreen.FRIEND_PROFILE))
            .map { HostScreen.Nested(it).key } + HostScreen.Tabs.key + HostScreen.Picker.key + "friend-profile"
        assertEquals(keys.size, keys.toSet().size)
    }
}
