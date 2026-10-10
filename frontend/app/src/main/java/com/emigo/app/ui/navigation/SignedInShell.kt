package com.emigo.app.ui.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emigo.app.BuildConfig
import com.emigo.app.EmberApplication
import com.emigo.app.ads.RestoreStreakWithAd
import com.emigo.app.ads.WatchAdForGallery
import com.emigo.app.ui.activity.ActivityViewModel
import com.emigo.app.ui.camera.CameraViewModel
import com.emigo.app.ui.components.FALLBACK_NAV_DOCK_HEIGHT_DP
import com.emigo.app.ui.components.NavDestination
import com.emigo.app.ui.friends.FriendsViewModel
import com.emigo.app.ui.friends.ProfileSubject
import com.emigo.app.ui.home.HomeViewModel
import com.emigo.app.ui.invite.InvitePromptSheet
import com.emigo.app.ui.invite.InviteViewModel
import com.emigo.app.ui.settings.EmberGoldScreen
import com.emigo.app.ui.settings.EmberGoldViewModel
import com.emigo.app.ui.theme.ThemeKey
import com.emigo.app.ui.theme.ThemeViewModel
import com.emigo.app.widget.WidgetPhotoSync
import com.emigo.app.widget.WidgetPreferenceStore
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** UI state that only exists while signed in. Created inside [SignedInShell], so it is thrown away
 * when the user signs out, exactly like the local variables it replaces. */
internal class ShellState {
    var showRecipientPicker by mutableStateOf(false)

    // Whether Home's featured photo is tapped into focus. Kept here, outside HomeScreen, so
    // it survives Home's page being rebuilt and so the shell can clear it when the pager
    // leaves Home (see the settledPage effect in SignedInShell).
    var isHomePhotoFocused by mutableStateOf(false)

    // Hoisted out of the pager for a visible bug: remembered next to the HorizontalPager,
    // every return from any nested screen reset it to FALLBACK_NAV_DOCK_HEIGHT_DP for one
    // frame before BottomNavDock's onSizeChanged corrected it. Every screen reserves bottom
    // space equal to this (see LocalNavDockHeight), so the guess-then-correct made the whole
    // page visibly shift ("vibrate"). Hoisting sets it once and never guesses again this
    // session.
    var navDockHeight by mutableStateOf(FALLBACK_NAV_DOCK_HEIGHT_DP)
}

/** Everything shown once signed in: the shared tab ViewModels and their background listeners,
 * the back-button rules, the nested screens or the pager (via [NestedScreenHost] and
 * [MainPager]), and the Gold screen on top. Composed by [EmberRoot] only while signed in. */
@Composable
internal fun SignedInShell(
    app: EmberApplication,
    nav: AppNavState,
    coldStart: ColdStartSnapshot,
    themeViewModel: ThemeViewModel,
    widgetPreferenceStore: WidgetPreferenceStore,
    widgetFeaturedFriendIds: Set<String>,
    isGoldMember: Boolean,
    onGoldActivated: () -> Unit,
    onPreviewTheme: (ThemeKey?) -> Unit,
    onSignOut: () -> Unit,
    coroutineScope: CoroutineScope,
    pendingNotificationIntent: () -> Intent?,
    onNotificationIntentHandled: () -> Unit,
) {
    val shell = remember { ShellState() }
    val appContext = LocalContext.current

    // The app's two core runtime permissions, asked together right after sign-in
    // instead of at first use. Camera used to be asked on arriving at the Camera
    // tab, gating the central action at the moment someone wanted it, and
    // notifications are needed before the first photo arrives. After sign-in, not
    // on the login screen, so nobody is prompted before committing to the app.
    //
    // CameraScreen keeps its own check and launcher: this is a convenience, and
    // someone who declines (or revokes later) still needs a way to grant it in
    // context.
    //
    // WRITE_EXTERNAL_STORAGE is not requested here: it exists only for saving to the
    // gallery on Android 9 and below (see the manifest's maxSdkVersion), so asking
    // everyone up front would cost a denial for nothing. It's asked when someone
    // taps save.
    val startupPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {}
    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.CAMERA)
            // POST_NOTIFICATIONS doesn't exist before Android 13; requesting it
            // there throws.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            // Only what isn't granted yet, so a returning user isn't re-prompted.
            ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) startupPermissionLauncher.launch(wanted.toTypedArray())
    }

    // Hoisted from HomeScreen so the nav dock, which lives outside it, can read Home's
    // live scroll position for the icon morph (as it used to read the outer pager's
    // swipe position).
    val homeScrollState = rememberScrollState()
    // Hoisted for another reason: FriendsScreen is fully disposed while a friend's
    // profile is open (the nestedScreen `when` composes one branch at a time), so a
    // scroll position it owned would reset to the top on every return.
    val friendsListState = rememberLazyListState()

    // Hoisted instead of one per tab: real-time backdrop blur sets up a GPU
    // render-effect pipeline (shader compile, capture buffers) on first use, and a
    // HazeState per tab rebuilt it on every tab switch, a consistent stutter on every
    // nav tap. One shared instance keeps it alive.
    val hazeState = rememberHazeState()

    // Hoisted (not declared inside their page branches) so they survive nested
    // screens and can be refreshed from elsewhere: FriendsViewModel after a friend is
    // removed, HomeViewModel after a photo is sent (streaks can change on send, not
    // just receive).
    val friendsViewModel: FriendsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                FriendsViewModel(
                    app.stringProvider,
                    app.friendRepository,
                    app.localListCache,
                    app.subscriptionRepository,
                    RestoreStreakWithAd(
                        ads = app.rewardedAds,
                        adUnitId = BuildConfig.ADMOB_RESTORE_STREAK_UNIT_ID,
                        // The server gets this back from Google with the watched ad.
                        myUserId = { app.userRepository.getMyProfile().map { it.userId } },
                        restore = app.friendRepository::restoreStreak,
                    ),
                    onFriendsChanged = { app.notifyFriendsChanged() },
                )
            }
        },
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    app.stringProvider,
                    app.photoRepository,
                    app.networkModule.tokenStore,
                    app.userRepository,
                    app.friendRepository,
                    app.localListCache,
                    coldStart.initialHomeCache,
                    onFeedLoaded = { items ->
                        coroutineScope.launch { WidgetPhotoSync.sync(app, items) }
                    },
                )
            }
        },
    )
    // A NEW_PHOTO push updates the widget directly (WidgetPhotoSync.syncFromPush in
    // EmberFirebaseMessagingService, no live app state needed); this is the other
    // half, letting a live HomeViewModel pick up the change too. loadFeed() only
    // updates syncedFeedItems (see HomeViewModel), never the visible session, so a
    // push landing mid-swipe can't interrupt; it only shows the "New memories
    // available" indicator.
    LaunchedEffect(Unit) {
        app.newPhotoPushEvents.collect { homeViewModel.loadFeed() }
    }
    // FriendsViewModel otherwise refetches only at app start or on pull-to-refresh,
    // so "Last sent" and streak on the Friends tab went stale when a friend's photo
    // arrived. A received photo changes exactly those fields, so it gets the same
    // treatment as Home's feed above.
    LaunchedEffect(Unit) {
        app.newPhotoPushEvents.collect { friendsViewModel.refreshSilently() }
    }
    // The other direction: a finished queued send (see PendingSendWorker) is my own
    // new photo, so both Feed and Memories need refreshing, unlike the push case
    // above, which needs only Feed.
    LaunchedEffect(Unit) {
        app.photoSendCompletedEvents.collect {
            homeViewModel.loadFeed()
            homeViewModel.loadMemories()
            // My own send changes streak and last activity for the recipient just
            // as receiving one does.
            friendsViewModel.refreshSilently()
        }
    }
    // Also hoisted: created here, its fetch starts when the app opens instead of on
    // the first tap of Activity, which made it feel slower than Home and Friends
    // (whose ViewModels were already hoisted).
    val activityViewModel: ActivityViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ActivityViewModel(app.stringProvider, app.activityRepository, app.localListCache) }
        },
    )
    // Camera is a pager page, not a screen created on entry; hoisted with the other tab
    // ViewModels so its state (notably capturedFile, which gates swiping, see
    // userScrollEnabled in MainPager) exists whichever page is current.
    val cameraViewModel: CameraViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CameraViewModel(
                    app.stringProvider,
                    app.friendRepository,
                    app.photoRepository,
                    app.subscriptionRepository,
                    app.localListCache,
                    app.cameraHintPreferenceStore,
                    app.galleryUnlock,
                    WatchAdForGallery(app.rewardedAds, BuildConfig.ADMOB_GALLERY_UNIT_ID, app.galleryUnlock),
                )
            }
        },
    )

    // Camera's recipient list (see hasLoadedCameraFriends) is fetched once per session
    // and never on its own afterward; without this, a friend request accepted
    // anywhere stayed invisible in Camera's picker until restart, since nothing told
    // this long-lived ViewModel its copy was stale.
    LaunchedEffect(Unit) {
        app.friendsChangedEvents.collect { cameraViewModel.loadFriends() }
    }
    LaunchedEffect(Unit) {
        app.friendsChangedEvents.collect { friendsViewModel.refreshSilently() }
    }
    // Flips the outbox button's animation from SENDING to its checkmark (see
    // CameraViewModel.markSendComplete for why this coarse, not photo-specific signal
    // is good enough). A separate collector because cameraViewModel isn't declared
    // yet at the earlier one.
    LaunchedEffect(Unit) {
        app.photoSendCompletedEvents.collect { cameraViewModel.markSendComplete() }
    }

    // "Add @ann?" for someone who just installed Emigo from ann's invite link. Almost always
    // nothing to show; looked for once per launch, quietly (see InviteReferral).
    val inviteViewModel: InviteViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                InviteViewModel(
                    app.stringProvider,
                    app.inviteReferral,
                    onRequestSent = { app.notifyFriendsChanged() },
                )
            }
        },
    )
    LaunchedEffect(Unit) { inviteViewModel.check() }
    LaunchedEffect(inviteViewModel.notice) {
        inviteViewModel.notice?.let {
            Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
            inviteViewModel.clearNotice()
        }
    }

    // Memories, Home, Camera, Friends and Settings are pages of one full-screen pager,
    // so a swipe moves between any of them, not just a nav-dock tap. Opens on Camera
    // (Snapchat/Locket-style: the default view is "take a photo", not a feed).
    // PAGE_HOME sits next to PAGE_CAMERA on purpose, so Home is one swipe away.
    val pagerState = rememberPagerState(initialPage = PAGE_CAMERA) { PAGE_COUNT }

    // The recipient-picker friends fetch (limit=500, see CameraViewModel) only needs
    // to happen once the user reaches Camera, not at launch. Fires once, the first
    // time the pager settles there, by button tap or by swiping.
    var hasLoadedCameraFriends by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage == PAGE_CAMERA && !hasLoadedCameraFriends) {
            hasLoadedCameraFriends = true
            // The Friends tab has often already fetched this list; reuse it instead of a
            // second, mostly redundant GET /friends when it's known complete (hasMore
            // == false). Otherwise fall back to CameraViewModel's own fetch (Friends
            // not loaded yet, or more than one page of friends). isLoading is checked
            // too: a snapshot hydrated from FriendsViewModel's disk cache can be an
            // incomplete first page with hasMore still at its pre-fetch default of
            // false, until the real fetch completes once.
            if (!friendsViewModel.isLoading && !friendsViewModel.hasMore && friendsViewModel.friends.isNotEmpty()) {
                cameraViewModel.provideFriends(friendsViewModel.friends)
            } else {
                cameraViewModel.loadFriends()
            }
        }
    }

    // Swiping away from Home mustn't leave the rest of the app blurred behind a focus
    // state Home no longer shows.
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != PAGE_HOME) shell.isHomePhotoFocused = false
    }
    // Clears the header bell's badge once Activity is actually the shown nested
    // screen, not on the bell tap alone ("only counts once visible", as when
    // Activity was a pager page keyed on settledPage).
    LaunchedEffect(nav.nestedScreen) {
        if (nav.nestedScreen == NestedScreen.ACTIVITY) activityViewModel.markSeen()
    }
    // cameraViewModel is Activity-scoped, not recreated per visit; without
    // discarding here, a capture the user swiped away from (not sent or retaken)
    // would still be in review the next time Camera came into view. Camera has no
    // close button now (it's a plain pager page), so this is the only place that
    // cleanup happens.
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != PAGE_CAMERA) {
            cameraViewModel.discardCapture()
            // The user has swiped away from Camera at least once, which is what the
            // onboarding hint taught. Permanent, one-way, on-device (see
            // CameraHintPreferenceStore).
            cameraViewModel.dismissSwipeHint()
        }
    }
    // Home's featured card has its own inner pager on the same axis, nested in this
    // one. Compose doesn't always hand a gesture off cleanly between same-axis
    // pagers, and the result was a drag ending with this pager stopped between two
    // pages. Instead of a fragile fix at the handoff, this is a general correction:
    // whenever a drag ends off a page boundary, animate to the nearest page.
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && abs(pagerState.currentPageOffsetFraction) > 0.01f) {
            val nearestPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .roundToInt()
                .coerceIn(0, PAGE_COUNT - 1)
            pagerState.animateScrollToPage(nearestPage)
        }
    }

    // Shared by the on-screen back arrow (FriendProfileScreen's onBack) and the system
    // back gesture below: two separate paths that both end this screen, once kept in
    // sync by hand (a fix that taught only one to refresh Friends left the commonly
    // used swipe/back button stale). It needn't refresh anything: every action here
    // (pin/unpin, remove, accept, decline) pushes its fresh result into
    // friendsViewModel on success (see onPinChanged/onRemoved/onAccepted/onRejected),
    // so it's already correct.
    val onCloseFriendProfile = {
        nav.nestedScreen = nav.friendProfileReturnTo
        nav.friendProfileReturnTo = null
        nav.selectedProfileSubject = null
    }

    // Back retraces the last navigation step instead of falling through to the system
    // (which closes the app): close the recipient picker, then any nested screen,
    // then return to Home before exiting. Camera has its own higher-priority
    // BackHandler (in CameraScreen) for "back retakes" while a capture is pending;
    // this one fires only once that no longer applies. It must stay above the screens
    // below: a BackHandler registered later wins, which is what gives theirs priority.
    BackHandler(
        enabled = shell.showRecipientPicker || nav.nestedScreen != null || pagerState.currentPage != PAGE_HOME,
    ) {
        when {
            shell.showRecipientPicker -> shell.showRecipientPicker = false
            nav.nestedScreen == NestedScreen.FRIEND_PROFILE -> onCloseFriendProfile()
            nav.nestedScreen != null -> nav.nestedScreen = null
            else -> coroutineScope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
        }
    }

    // A nav dock tap (a tab or the camera button) is a direct jump, not a swipe, so it
    // shouldn't animate through every page in between. scrollToPage snaps straight
    // there; only an actual drag animates through the pages it crosses.
    val onNavigate: (NavDestination) -> Unit = { destination ->
        nav.nestedScreen = null
        // Tapping Home while already on Home scrolls back to the top, like "tap the
        // tab again" in other apps, instead of doing nothing because the page
        // didn't change.
        if (destination == NavDestination.HOME) {
            coroutineScope.launch { homeScrollState.animateScrollTo(0) }
        }
        coroutineScope.launch { pagerState.scrollToPage(pageForDestination(destination)) }
    }
    val onCameraClick: () -> Unit = {
        coroutineScope.launch { pagerState.scrollToPage(PAGE_CAMERA) }
    }

    /** The profile subject behind an activity row's actor, or null if they can't be
     * placed. Pending requests are checked first so someone with a request still
     * waiting opens as a PendingRequest, offering accept/decline, which the plain
     * Friend case wouldn't. */
    val resolveActorSubject: (String) -> ProfileSubject? = { actorId ->
        friendsViewModel.pendingRequests.firstOrNull { it.requesterId == actorId }
            ?.let { ProfileSubject.PendingRequest(it) }
            ?: friendsViewModel.friends.firstOrNull { it.friendId == actorId }
                ?.let { ProfileSubject.Friend(it) }
    }

    // Handles a tap on the streak-broken notification's "Restore streak" action (see
    // EmberFirebaseMessagingService.showStreakBrokenNotification, the only place that
    // sets these extras): the same Gold-or-restore branch as FriendsScreen's restore
    // pill, reached from outside the Compose tree. Keyed on the intent itself so a
    // second, different notification tap restarts the effect instead of being
    // ignored. Clears the pending intent once handled (or found irrelevant) so
    // recomposition can't replay it.
    LaunchedEffect(pendingNotificationIntent()) {
        val intent = pendingNotificationIntent() ?: return@LaunchedEffect
        if (intent.getStringExtra(EXTRA_NOTIFICATION_ACTION) == NOTIFICATION_ACTION_RESTORE_STREAK) {
            val friendshipId = intent.getStringExtra(EXTRA_STREAK_FRIENDSHIP_ID)
            if (friendshipId != null) {
                if (app.subscriptionRepository.isGoldMemberOrLastKnown()) {
                    friendsViewModel.restoreStreak(friendshipId)
                    onNavigate(NavDestination.FRIENDS)
                } else {
                    // Without Gold the choice is a watched ad or Gold, in the same sheet the
                    // Friends tab's own restore pill opens.
                    friendsViewModel.offerRestoreChoice(friendshipId)
                    onNavigate(NavDestination.FRIENDS)
                }
            }
        }
        onNotificationIntentHandled()
    }

    // Wraps the nested screens and the Gold overlay so Gold renders on top of whatever
    // NestedScreenHost shows, instead of being another mutually exclusive branch.
    Box(modifier = Modifier.fillMaxSize()) {
        NestedScreenHost(
            app = app,
            nav = nav,
            shell = shell,
            coldStart = coldStart,
            themeViewModel = themeViewModel,
            homeViewModel = homeViewModel,
            friendsViewModel = friendsViewModel,
            activityViewModel = activityViewModel,
            cameraViewModel = cameraViewModel,
            widgetPreferenceStore = widgetPreferenceStore,
            hazeState = hazeState,
            coroutineScope = coroutineScope,
            onPreviewTheme = onPreviewTheme,
            onSignOut = onSignOut,
            onNavigate = onNavigate,
            onCameraClick = onCameraClick,
            onCloseFriendProfile = onCloseFriendProfile,
            resolveActorSubject = resolveActorSubject,
        ) {
            MainPager(
                app = app,
                nav = nav,
                shell = shell,
                themeViewModel = themeViewModel,
                homeViewModel = homeViewModel,
                friendsViewModel = friendsViewModel,
                activityViewModel = activityViewModel,
                cameraViewModel = cameraViewModel,
                pagerState = pagerState,
                homeScrollState = homeScrollState,
                friendsListState = friendsListState,
                hazeState = hazeState,
                isGoldMember = isGoldMember,
                widgetFeaturedFriendIds = widgetFeaturedFriendIds,
                coroutineScope = coroutineScope,
                onSignOut = onSignOut,
                onNavigate = onNavigate,
                onCameraClick = onCameraClick,
            )
        }

        // Always composed (not gated inside NestedScreenHost) so its exit transition has
        // something to animate; a screen selected by a `when` leaves composition the
        // instant its condition flips, before an exit animation could play. The one
        // nested screen with its own entrance: it slides up like a sheet every time
        // instead of appearing instantly.
        AnimatedVisibility(
            visible = nav.nestedScreen == NestedScreen.GOLD,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            val goldViewModel: EmberGoldViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { EmberGoldViewModel(app.stringProvider, app.billingManager, app.subscriptionRepository) }
                },
            )
            EmberGoldScreen(
                viewModel = goldViewModel,
                onBack = { nav.nestedScreen = null },
                onGoldActivated = onGoldActivated,
            )
        }

        inviteViewModel.inviter?.let { inviter ->
            InvitePromptSheet(
                inviter = inviter,
                isSending = inviteViewModel.isSending,
                onAdd = inviteViewModel::accept,
                onDismiss = inviteViewModel::dismiss,
            )
        }
    }
}
