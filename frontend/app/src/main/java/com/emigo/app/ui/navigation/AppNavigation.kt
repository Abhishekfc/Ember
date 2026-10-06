package com.emigo.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.emigo.app.ui.components.NavDestination
import com.emigo.app.ui.friends.ProfileSubject

/** Screens reached from within a tab (Settings -> Theme, Friends -> Find People / Friend Profile)
 * rather than from the bottom nav. Kept apart from the current page so back pops just the nested
 * screen without losing which page you were on. Camera isn't one of these: it's a swipeable page
 * of the main pager. Activity is, since it moved from the nav dock to the bell in Home's header. */
internal enum class NestedScreen { THEME, FIND_PEOPLE, FRIEND_PROFILE, PROFILE, GOLD, WIDGET_SETTINGS, BLOCKED_USERS, OTHER_SETTINGS, SENT_PHOTOS, ACTIVITY }

/** Intent extras a notification's action button (or its body tap) can carry to route to an in-app
 * action once MainActivity is showing. Only the streak-restore notification uses them (see
 * EmberFirebaseMessagingService.showStreakBrokenNotification). */
const val EXTRA_NOTIFICATION_ACTION = "notification_action"
const val EXTRA_STREAK_FRIENDSHIP_ID = "streak_friendship_id"
const val NOTIFICATION_ACTION_RESTORE_STREAK = "restore_streak"

/** The pager's page order, left to right, matching the bottom nav (Memories, Home, [Camera in the
 * center], Friends, Settings). Home sits next to Camera on purpose: the app opens on Camera (the
 * pager's initialPage), and one swipe from it must land on Home, not Memories. Activity has no
 * page of its own, so swiping past Friends or Settings never lands on it. */
internal const val PAGE_MEMORIES = 0
internal const val PAGE_HOME = 1
internal const val PAGE_CAMERA = 2
internal const val PAGE_FRIENDS = 3
internal const val PAGE_SETTINGS = 4
internal const val PAGE_COUNT = 5

internal fun pageForDestination(destination: NavDestination): Int = when (destination) {
    NavDestination.MEMORIES -> PAGE_MEMORIES
    NavDestination.HOME -> PAGE_HOME
    NavDestination.FRIENDS -> PAGE_FRIENDS
    NavDestination.SETTINGS -> PAGE_SETTINGS
}

/** The nav-dock tab that reads as active for a page. Camera has no tab (its icon fades out near
 * that page, see the dock's alpha graphicsLayer in MainPager), so it falls back to Home. */
internal fun destinationForPage(page: Int): NavDestination = when (page) {
    PAGE_MEMORIES -> NavDestination.MEMORIES
    PAGE_HOME -> NavDestination.HOME
    PAGE_FRIENDS -> NavDestination.FRIENDS
    PAGE_SETTINGS -> NavDestination.SETTINGS
    else -> NavDestination.HOME
}

/** Which nested screen is open, plus the friend-profile bookkeeping around it. Created in
 * [EmberRoot] (above the sign-in gate, where these three values have always lived), so sign-out
 * resets them the same way it always has. */
internal class AppNavState {
    var nestedScreen by mutableStateOf<NestedScreen?>(null)
    var selectedProfileSubject by mutableStateOf<ProfileSubject?>(null)

    // Where closing the friend profile lands. nestedScreen holds one screen, not a back
    // stack, so a profile opened from another nested screen (Activity, Find People) must
    // return there instead of falling through to the pager (which lost your Find People
    // search results). Null means opened from a pager tab. Every site that opens a
    // profile sets this explicitly, so a value never lingers from an earlier visit.
    var friendProfileReturnTo by mutableStateOf<NestedScreen?>(null)
}
