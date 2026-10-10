package com.emigo.app.ui.navigation

import com.emigo.app.ui.friends.ProfileSubject

/**
 * Which page [NestedScreenHost] is showing: the main tabs, the recipient picker, or one nested
 * page. Pulled out of the host's old `when` so the host can animate between pages: the page
 * leaving has to keep drawing what it was showing, not whatever the navigation state says by then
 * (a profile's person is gone from the state the moment it is closed).
 *
 * [key] says whether two values are "the same page". Same key means no transition, so a profile
 * whose person updates in place (a request accepted, say) does not slide.
 *
 * [depth] is how far in the page sits: the tabs are 0, a page opened from them 1, and a profile
 * opened from Find friends or Activity 2. Moving to a deeper page pushes (slides in from the
 * right); moving to a shallower one pops (slides out to the right), like the iPhone.
 */
internal sealed interface HostScreen {
    val key: String
    val depth: Int

    data object Tabs : HostScreen {
        override val key = "tabs"
        override val depth = 0
    }

    data object Picker : HostScreen {
        override val key = "picker"
        override val depth = 1
    }

    data class Nested(val screen: NestedScreen) : HostScreen {
        override val key = "nested-${screen.name}"
        override val depth = 1
    }

    data class FriendProfile(val subject: ProfileSubject, override val depth: Int) : HostScreen {
        override val key = "friend-profile"
    }
}

/** The page to show for the current navigation state. Same precedence as the host's old `when`:
 * a nested page wins over the picker, which wins over the tabs. The Gold page is not drawn by the
 * host (it is an overlay), so it falls through to whatever is underneath. A friend profile with no
 * person to show also falls through. */
internal fun hostScreenFor(
    nestedScreen: NestedScreen?,
    profileSubject: ProfileSubject?,
    profileReturnTo: NestedScreen?,
    showRecipientPicker: Boolean,
): HostScreen = when {
    nestedScreen == NestedScreen.FRIEND_PROFILE && profileSubject != null ->
        HostScreen.FriendProfile(
            subject = profileSubject,
            // Reached from another nested page (Find friends, Activity): one level deeper than that page.
            depth = if (profileReturnTo == NestedScreen.FIND_PEOPLE || profileReturnTo == NestedScreen.ACTIVITY) 2 else 1,
        )
    nestedScreen != null && nestedScreen != NestedScreen.GOLD && nestedScreen != NestedScreen.FRIEND_PROFILE ->
        HostScreen.Nested(nestedScreen)
    showRecipientPicker -> HostScreen.Picker
    else -> HostScreen.Tabs
}

/** Slide in from the right (push) when going to an equal or deeper page, slide out to the right
 * (pop) when going back to a shallower one. */
internal fun isPush(from: HostScreen, to: HostScreen): Boolean = to.depth >= from.depth
