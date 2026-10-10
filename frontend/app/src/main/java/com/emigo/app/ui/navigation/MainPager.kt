package com.emigo.app.ui.navigation

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.emigo.app.EmberApplication
import com.emigo.app.R
import com.emigo.app.ui.activity.ActivityViewModel
import com.emigo.app.ui.camera.CameraScreen
import com.emigo.app.ui.camera.CameraViewModel
import androidx.compose.ui.platform.LocalContext
import com.emigo.app.ui.auth.inviteMessageFor
import com.emigo.app.ui.auth.shareInvite
import com.emigo.app.ui.components.BottomNavDock
import com.emigo.app.ui.components.LocalNavDockHeight
import com.emigo.app.ui.components.NavDestination
import com.emigo.app.ui.friends.FriendsScreen
import com.emigo.app.ui.friends.FriendsViewModel
import com.emigo.app.ui.friends.ProfileSubject
import com.emigo.app.ui.home.HomeScreen
import com.emigo.app.ui.home.HomeViewModel
import com.emigo.app.ui.memories.MemoriesTabScreen
import com.emigo.app.ui.settings.SettingsScreen
import com.emigo.app.ui.theme.ThemeViewModel
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Memories, Home, Camera, Friends and Settings as pages of one full-screen pager, with the
 * bottom nav dock floating over them. Shown by [SignedInShell] whenever no nested screen is open. */
@Composable
internal fun MainPager(
    app: EmberApplication,
    nav: AppNavState,
    shell: ShellState,
    themeViewModel: ThemeViewModel,
    homeViewModel: HomeViewModel,
    friendsViewModel: FriendsViewModel,
    activityViewModel: ActivityViewModel,
    cameraViewModel: CameraViewModel,
    pagerState: PagerState,
    homeScrollState: ScrollState,
    friendsListState: LazyListState,
    hazeState: HazeState,
    isGoldMember: Boolean,
    widgetFeaturedFriendIds: Set<String>,
    coroutineScope: CoroutineScope,
    onSignOut: () -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onCameraClick: () -> Unit,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        // navDockHeight is hoisted into ShellState (see its comment there). Screens read it via
        // LocalNavDockHeight instead of a fixed dp, which once left Settings' Log out button
        // partly under the dock on a real device. defaultOverscrollFactory is captured here,
        // before the pager's scope nulls the local, and re-provided inside page content so each
        // tab's vertical lists keep normal overscroll; only the pager's horizontal edge-of-tabs
        // bounce is disabled.
        val defaultOverscrollFactory = LocalOverscrollFactory.current
        CompositionLocalProvider(
            LocalNavDockHeight provides shell.navDockHeight,
            // Swiping past the first (Memories) or last (Settings) tab stretched
            // and bounced like a list at its end, which for tab navigation read
            // as hitting a wall and doesn't fit this app's flat, no-bounce
            // design. null turns that off for the pager only.
            LocalOverscrollFactory provides null,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Keeps the current tab's neighbours composed instead of tearing them
                // down when you swipe away. This fixes Home's featured card briefly
                // showing the previous photo on return: rebuilding Home recreates its
                // card pager, which restores its page number at once but applies the
                // scroll offset needed to show that page a frame later, so one frame
                // painted the page before. Logging confirmed the page index was
                // always already correct, which is why adjusting it never helped. Not
                // rebuilding the screen removes the wrong frame instead of correcting
                // it after it's drawn. Grows with the distance from Home (see
                // beyondViewportPagesFor) so the same holds when coming back from Friends or
                // Settings, which are further away than one page.
                beyondViewportPageCount = beyondViewportPagesFor(pagerState.currentPage),
                // A photo mid-review or caption is easy to lose to an accidental
                // swipe; once captured, swiping is blocked until it's sent or
                // discarded.
                userScrollEnabled = cameraViewModel.capturedFile == null,
                // The default threshold needs a drag across ~50% of the screen
                // before release commits to the next page, so a short flick
                // snapped back. Lowered so a light flick is enough.
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapPositionalThreshold = 0.2f,
                ),
            ) { page ->
                CompositionLocalProvider(LocalOverscrollFactory provides defaultOverscrollFactory) {
                    when (page) {
                        PAGE_MEMORIES -> MemoriesTabScreen(
                            viewModel = homeViewModel,
                            onCameraClick = onCameraClick,
                            hazeState = hazeState,
                        )

                        PAGE_HOME -> HomeScreen(
                            viewModel = homeViewModel,
                            onCameraClick = onCameraClick,
                            onAddFriendClick = { nav.nestedScreen = NestedScreen.FIND_PEOPLE },
                            onProfileClick = { nav.nestedScreen = NestedScreen.PROFILE },
                            onActivityClick = { nav.nestedScreen = NestedScreen.ACTIVITY },
                            activityBadgeCount = activityViewModel.newActivityCount,
                            hazeState = hazeState,
                            isPhotoFocused = shell.isHomePhotoFocused,
                            onToggleFocus = { shell.isHomePhotoFocused = !shell.isHomePhotoFocused },
                            onDismissFocus = { shell.isHomePhotoFocused = false },
                            scrollState = homeScrollState,
                            isActive = pagerState.settledPage == PAGE_HOME,
                            hasSharedRecently = cameraViewModel.lastSentPhotoUrl != null,
                        )

                        PAGE_FRIENDS -> FriendsScreen(
                            viewModel = friendsViewModel,
                            onCameraClick = onCameraClick,
                            onFindPeopleClick = { nav.nestedScreen = NestedScreen.FIND_PEOPLE },
                            onFriendClick = { friend ->
                                nav.selectedProfileSubject = ProfileSubject.Friend(friend)
                                nav.friendProfileReturnTo = null
                                nav.nestedScreen = NestedScreen.FRIEND_PROFILE
                            },
                            onPendingRequestClick = { request ->
                                nav.selectedProfileSubject = ProfileSubject.PendingRequest(request)
                                nav.friendProfileReturnTo = null
                                nav.nestedScreen = NestedScreen.FRIEND_PROFILE
                            },
                            onUpgradeToGold = { nav.nestedScreen = NestedScreen.GOLD },
                            hazeState = hazeState,
                            listState = friendsListState,
                        )

                        PAGE_CAMERA -> CameraScreen(
                            viewModel = cameraViewModel,
                            onOpenRecipientPicker = { shell.showRecipientPicker = true },
                            onUpgradeToGold = { nav.nestedScreen = NestedScreen.GOLD },
                            onOpenSentPhotos = { nav.nestedScreen = NestedScreen.SENT_PHOTOS },
                            // From the "no friends yet" sheet. The photo stays on the review screen
                            // (the camera's state outlives these screens), so coming back resumes it.
                            onAddFriend = { nav.nestedScreen = NestedScreen.FIND_PEOPLE },
                            onInviteFriends = { shareInvite(context, inviteMessageFor(context, homeViewModel.username), null) },
                            onSent = {
                                // Fires when the photo is queued, not once uploaded:
                                // PendingSendWorker sends it in the background
                                // (possibly much later without connectivity). Stays
                                // on Camera, whose header shows Sending/Sent, so
                                // there's no need to leave to see the outcome. These
                                // two still refresh right away, quietly, so Home and
                                // Memories have the real photo by the time the user
                                // swipes there.
                                homeViewModel.loadFeed()
                                homeViewModel.loadMemories()
                            },
                        )

                        else -> {
                            val notificationsEnabled by app.notificationPreferenceStore.enabled
                                .collectAsState(initial = true)
                            SettingsScreen(
                                displayName = homeViewModel.userName,
                                username = homeViewModel.username,
                                profilePhotoUrl = homeViewModel.profilePhotoUrl,
                                currentTheme = themeViewModel.selectedTheme,
                                isGoldMember = isGoldMember,
                                widgetBadge = if (widgetFeaturedFriendIds.isEmpty()) {
                                    stringResource(R.string.widget_badge_anyone)
                                } else {
                                    pluralStringResource(
                                        R.plurals.widget_badge_friends,
                                        widgetFeaturedFriendIds.size,
                                        widgetFeaturedFriendIds.size,
                                    )
                                },
                                notificationsEnabled = notificationsEnabled,
                                onNotificationsChange = { enabled ->
                                    coroutineScope.launch { app.notificationPreferenceStore.save(enabled) }
                                },
                                onCameraClick = onCameraClick,
                                onProfileClick = { nav.nestedScreen = NestedScreen.PROFILE },
                                onThemeClick = { nav.nestedScreen = NestedScreen.THEME },
                                onGoldClick = { nav.nestedScreen = NestedScreen.GOLD },
                                onWidgetClick = { nav.nestedScreen = NestedScreen.WIDGET_SETTINGS },
                                onBlockedUsersClick = { nav.nestedScreen = NestedScreen.BLOCKED_USERS },
                                onOtherClick = { nav.nestedScreen = NestedScreen.OTHER_SETTINGS },
                                onSignOut = onSignOut,
                                hazeState = hazeState,
                            )
                        }
                    }
                }
            }

            BottomNavDock(
                active = destinationForPage(pagerState.currentPage),
                onNavigate = onNavigate,
                onCameraClick = onCameraClick,
                friendsBadgeCount = friendsViewModel.pendingRequests.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Fades the dock out near the Camera page (its capture and review
                    // controls use similar bottom space). A graphicsLayer lambda
                    // defers the alpha to the draw phase so it doesn't recompose on
                    // every swipe frame.
                    .graphicsLayer {
                        alpha = abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - PAGE_CAMERA)
                            .coerceIn(0f, 1f)
                    },
                hazeState = hazeState,
                onHeightMeasured = { shell.navDockHeight = it },
            )
        }
    }
}
