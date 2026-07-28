package com.ember.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.ember.app.data.FirstPhotoPreloader
import com.ember.app.data.local.LocalListCache
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.MemoryPhotoDto
import com.ember.app.data.remote.dto.UserProfileDto
import com.ember.app.ui.activity.ActivityScreen
import com.ember.app.ui.activity.ActivityViewModel
import com.ember.app.ui.auth.AuthPalette
import com.ember.app.ui.auth.LoginScreen
import com.ember.app.ui.auth.LoginViewModel
import com.ember.app.ui.camera.CameraScreen
import com.ember.app.ui.camera.CameraViewModel
import com.ember.app.ui.camera.RecipientPickerScreen
import com.ember.app.ui.camera.RecipientPickerViewModel
import com.ember.app.ui.components.BottomNavDock
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.friends.FindPeopleScreen
import com.ember.app.ui.friends.FindPeopleViewModel
import com.ember.app.ui.friends.FriendProfileScreen
import com.ember.app.ui.friends.FriendProfileViewModel
import com.ember.app.ui.friends.FriendsScreen
import com.ember.app.ui.friends.FriendsViewModel
import com.ember.app.ui.friends.ProfileSubject
import com.ember.app.ui.home.HomeScreen
import com.ember.app.ui.home.HomeViewModel
import com.ember.app.ui.home.InitialHomeCache
import com.ember.app.ui.home.MEMORIES_REVEAL_SCROLL_DP
import com.ember.app.ui.profile.MyProfileScreen
import com.ember.app.ui.profile.MyProfileViewModel
import com.ember.app.ui.settings.EmberGoldScreen
import com.ember.app.ui.settings.SettingsScreen
import com.ember.app.ui.theme.EmberAppTheme
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.ThemeKey
import com.ember.app.ui.theme.ThemeScreen
import com.ember.app.ui.theme.ThemeViewModel
import com.ember.app.widget.EmberWidget
import com.ember.app.widget.WidgetPhotoStore
import com.ember.app.widget.WidgetPhotoSync
import com.ember.app.widget.WidgetUpdateWorker
import androidx.glance.appwidget.updateAll
import com.google.firebase.messaging.FirebaseMessaging
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** Screens reached from within a tab (Settings -> Theme, Friends -> Find People / Friend
 * Profile) rather than from the bottom nav tabs directly. Kept separate from the current page
 * so back navigation can pop just the nested screen without losing which page you were on.
 * Camera is NOT one of these any more — it's a swipeable page of the main pager, same as Home
 * or Friends, not a modal reached from a button. */
private enum class NestedScreen { THEME, FIND_PEOPLE, FRIEND_PROFILE, PROFILE, GOLD }

/** The unified pager's page order — left to right, matching the bottom nav's own visual layout
 * (Home, Friends, [Camera in the center], Activity, Settings). Memories is no longer a page of
 * its own — it lives inline inside Home's scrollable content now (below the avatar row, or below
 * the empty-state message), reachable by scrolling down rather than swiping to a separate page. */
private const val PAGE_HOME = 0
private const val PAGE_FRIENDS = 1
private const val PAGE_CAMERA = 2
private const val PAGE_ACTIVITY = 3
private const val PAGE_SETTINGS = 4
private const val PAGE_COUNT = 5

private fun pageForDestination(destination: NavDestination): Int = when (destination) {
    NavDestination.HOME -> PAGE_HOME
    NavDestination.FRIENDS -> PAGE_FRIENDS
    NavDestination.ACTIVITY -> PAGE_ACTIVITY
    NavDestination.SETTINGS -> PAGE_SETTINGS
}

/** Which nav-dock tab should read as "active" for a given page — Camera doesn't correspond to
 * any tab (its icon fades out entirely near that page instead, see the dock's own alpha
 * graphicsLayer below), so it falls back to Home. */
private fun destinationForPage(page: Int): NavDestination = when (page) {
    PAGE_HOME -> NavDestination.HOME
    PAGE_FRIENDS -> NavDestination.FRIENDS
    PAGE_ACTIVITY -> NavDestination.ACTIVITY
    PAGE_SETTINGS -> NavDestination.SETTINGS
    else -> NavDestination.HOME
}

class MainActivity : ComponentActivity() {

    // All hoisted to EmberApplication (see its own doc comment) so they're true process-wide
    // singletons — surviving this Activity being destroyed/recreated by a config change, rather
    // than each recreation quietly wiring every ViewModel's repository to a orphaned NetworkModule.
    private val emberApplication get() = application as EmberApplication
    private val networkModule get() = emberApplication.networkModule
    private val authRepository get() = emberApplication.authRepository
    private val photoRepository get() = emberApplication.photoRepository
    private val friendRepository get() = emberApplication.friendRepository
    private val activityRepository get() = emberApplication.activityRepository
    private val userRepository get() = emberApplication.userRepository
    private val subscriptionRepository get() = emberApplication.subscriptionRepository
    private val themePreferenceStore get() = emberApplication.themePreferenceStore
    private val notificationPreferenceStore get() = emberApplication.notificationPreferenceStore
    private val localListCache get() = emberApplication.localListCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // True edge-to-edge: the status/nav bars are fully transparent, and every screen's own
        // full-bleed background gradient (already drawn via Modifier.fillMaxSize().background(...)
        // everywhere) paints straight through behind them — this is what actually makes the bars
        // disappear into the screen pixel-for-pixel, rather than approximating with a flat fill
        // color (tried first; visibly seams on any theme whose background isn't flat across the
        // top edge, e.g. Ember's off-center radial gradient). Content that would otherwise render
        // underneath the bars now needs its own explicit statusBarsPadding()/navigationBarsPadding()
        // — see each top-level screen for where that's applied.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Otherwise the system quietly paints a translucent dark scrim behind the gesture bar
            // for legibility contrast, which is exactly the flat mismatched strip this whole fix
            // is meant to remove — every screen already gives its own content enough contrast
            // against its own background without needing the system's help here.
            window.isNavigationBarContrastEnforced = false
        }
        // Keep the widget's background refresh alive even across reinstalls that wiped the
        // schedule; KEEP policy makes this a no-op when it's already queued.
        WidgetUpdateWorker.schedule(applicationContext)
        // Read once, synchronously, before the first frame — a returning user's very first
        // composition should already show real content (feed items, cached Memories thumbnails,
        // the profile picture), not an empty/default state that then pops to the right thing a
        // moment later once some async read resolves. That's what produced a visible "everything
        // reloads" flash on every restart even though the data itself was genuinely cached — the
        // read just wasn't finished before the first frame drew. A handful of tiny local
        // Preferences reads block for a few milliseconds at most, well before there's anything on
        // screen yet to notice a delay in.
        // var, not val — reset back to empty on sign-out (see onSignOut below) rather than left
        // holding whichever account was signed in when this Activity was created. Without that
        // reset, signing into a different account within the same process reused this same
        // already-read snapshot as the new HomeViewModel's "instant on reopen" seed, briefly (or,
        // combined with the repositories' own account-unaware TTL caches below, not so briefly)
        // showing the previous account's feed/memories/profile until a later fetch overwrote it.
        var initialHomeCache = runBlocking {
            InitialHomeCache(
                feedItems = localListCache.read<FeedItem>(LocalListCache.KEY_FEED) ?: emptyList(),
                memories = localListCache.read<MemoryPhotoDto>(LocalListCache.KEY_MEMORIES) ?: emptyList(),
                profile = localListCache.readObject<UserProfileDto>(LocalListCache.KEY_PROFILE),
            )
        }
        // Just the one photo Home's featured card shows first (page 0 — see buildHomeCarousel/
        // pageIndexFor in HomeScreen.kt: the first feed item's newest photo) — asking Coil for it
        // this early, before Compose has even started, gives it a real head start on what would
        // otherwise be the very first AsyncImage request of the whole session. Sized to the real
        // screen width (not left at Coil's full-original-resolution default) — see
        // FirstPhotoPreloader's own doc comment for why an unsized preload of one of this app's
        // multi-MB test images was actually part of the problem, not just "not enough of a fix."
        FirstPhotoPreloader.preload(
            applicationContext,
            initialHomeCache.feedItems.firstOrNull()?.photos?.lastOrNull()?.photoUrl,
            targetWidthPx = resources.displayMetrics.widthPixels,
        )
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ThemeViewModel(themePreferenceStore) }
                },
            )
            // Non-null only while ThemeScreen is being browsed with an unapplied pick staged —
            // lets the whole app (this screen included) live-preview a theme before it's
            // actually chosen, without touching the persisted selectedTheme until Apply is
            // tapped. ThemeScreen clears this back to null itself, both on Apply and whenever
            // it leaves composition (back button, navigating away) so an unapplied preview never
            // lingers past the screen that was browsing it.
            var previewThemeKey by remember { mutableStateOf<ThemeKey?>(null) }

            // Hoisted above EmberAppTheme so picking a theme on ThemeScreen re-themes the
            // whole app immediately, not just that screen.
            EmberAppTheme(themeKey = previewThemeKey ?: themeViewModel.selectedTheme) {
                val loginViewModel: LoginViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { LoginViewModel(authRepository) }
                    },
                )
                var authenticated by remember { mutableStateOf(false) }
                // Distinct from `authenticated`: this only tracks whether we've finished reading
                // the saved token yet, so a returning user with a valid session doesn't flash
                // the login screen for a frame while that DataStore read is in flight.
                var sessionChecked by remember { mutableStateOf(false) }
                var nestedScreen by remember { mutableStateOf<NestedScreen?>(null) }
                var selectedProfileSubject by remember { mutableStateOf<ProfileSubject?>(null) }
                val appContext = LocalContext.current

                // Bars themselves are fully transparent (set once, in onCreate) — the only thing
                // that needs updating per-recomposition is which set of icons (light content for
                // a dark background, dark content for a light one) reads correctly on top of
                // whatever's now showing through: the active theme's colors once signed in, or
                // the fixed Ember look AuthPalette uses pre-login.
                val barsAreLight = if (authenticated) EmberTheme.colors.isLight else AuthPalette.colors.isLight
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = barsAreLight
                    insetsController.isAppearanceLightNavigationBars = barsAreLight
                }

                // Camera is now a page you can swipe to rather than a screen only opened
                // deliberately, so the multi-hundred-millisecond ProcessCameraProvider fetch
                // (previously eaten silently the first time someone tapped the camera button)
                // is far more likely to show up as a visible black flash mid-swipe. Resolving it
                // once, this early, means it's very likely already warm in memory (CameraX keeps
                // it as a process-wide singleton) by the time the Camera page is actually
                // reached — this doesn't touch the separate bindToLifecycle cost in CameraScreen
                // itself, so it won't eliminate the flash entirely, only shorten it.
                LaunchedEffect(Unit) { ProcessCameraProvider.getInstance(appContext) }
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    authenticated = networkModule.tokenStore.currentToken() != null
                    sessionChecked = true
                }

                // Shared by the manual "Sign out" button in Settings and by the automatic
                // handler below for an expired/invalid token (a 401 on an authenticated
                // request) — both need to land the user back on a clean login screen.
                val onSignOut = {
                    coroutineScope.launch { networkModule.tokenStore.clear() }
                    // LocalListCache isn't scoped per-account — without this, a different user
                    // signing in on the same device would briefly see this account's cached
                    // feed/friends/activity/memories before the fresh fetch overwrites them.
                    coroutineScope.launch { localListCache.clearAll() }
                    // Reset alongside LocalListCache itself (see this var's own doc comment at
                    // declaration) — otherwise the next HomeViewModel constructed after signing
                    // back in still seeds from this account's already-read-into-memory snapshot,
                    // which clearAll() (an on-disk clear) has no effect on.
                    initialHomeCache = InitialHomeCache()
                    // These repositories are process-wide singletons (see EmberApplication) that
                    // outlive any one signed-in account, but their TTL caches are keyed without
                    // any account identity — without clearing them here, signing into a different
                    // account within that ~30s window could serve the *previous* account's feed/
                    // friends/activity straight out of cache on what looks like a normal fetch,
                    // not fixable by anything short of a force-refresh (or, as reported, waiting
                    // out the TTL) otherwise.
                    photoRepository.clearCache()
                    friendRepository.clearCache()
                    activityRepository.clearCache()
                    // The widget reads its cached photo independent of sign-in state — without
                    // this, a friend's private photo (and their name) keeps rendering on the
                    // home screen indefinitely after "signing out."
                    coroutineScope.launch {
                        WidgetPhotoStore(applicationContext).clear()
                        EmberWidget().updateAll(applicationContext)
                    }
                    // All per-account ViewModels (home feed, friends list, login form, etc.)
                    // live in the Activity's ViewModelStore and are normally retrieved by
                    // class/key regardless of how many times `authenticated` flips — without
                    // clearing here, signing into a different account would keep showing the
                    // previous account's cached feed, friends, and stale form fields.
                    viewModelStore.clear()
                    authenticated = false
                    nestedScreen = null
                    selectedProfileSubject = null
                }

                // The backend issues short-lived JWTs with no refresh flow yet, so a session
                // will eventually 401 on its own — without this, the app would sit on a
                // permanently broken "couldn't load" error instead of returning to login.
                LaunchedEffect(Unit) {
                    networkModule.sessionExpired.collect { onSignOut() }
                }

                // Re-fires on every genuine authenticated-state transition — a fresh login, or an
                // already-valid session found at cold start (see the LaunchedEffect(Unit) above)
                // are both moments a token and a signed-in user first coexist. A token obtained
                // *before* this fires (e.g. EmberFirebaseMessagingService.onNewToken landing
                // while signed out) is skipped there specifically so this is never missed —
                // fetching the current token here rather than relying on that callback having
                // already run covers both orderings with one path.
                LaunchedEffect(authenticated) {
                    if (authenticated) {
                        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                        if (token != null) authRepository.registerDeviceToken(token)
                    }
                }

                if (!sessionChecked) {
                    Box(modifier = Modifier.fillMaxSize().background(EmberTheme.colors.background.asBrush(Size.Zero)))
                } else if (!authenticated) {
                    LoginScreen(viewModel = loginViewModel, onAuthenticated = { authenticated = true })
                } else {
                    var showRecipientPicker by remember { mutableStateOf(false) }

                    // Android 13+ suppresses every notification (including the ones
                    // EmberFirebaseMessagingService posts for a NEW_PHOTO push) until this is
                    // granted. Asked here rather than on the login screen, matching the usual
                    // convention of asking right after sign-in rather than before someone's even
                    // committed to using the app.
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) {}
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // Tapping the featured photo on Home throws everything else out of focus,
                    // the shared nav dock included — hoisted all the way up here (rather than
                    // living inside HomeScreen) since the dock is now a single instance shared
                    // across every page, not something each screen renders for itself.
                    var isHomePhotoFocused by remember { mutableStateOf(false) }

                    // Hoisted up from HomeScreen (rather than let it own its own rememberScrollState)
                    // so the nav dock — which lives outside HomeScreen entirely, alongside every
                    // other page — can read Home's live scroll position for the icon-morph below,
                    // the same way it used to read the outer pager's swipe position.
                    val homeScrollState = rememberScrollState()
                    // Same distance HomeScreen's own Memories-grid fade-in uses (see
                    // MEMORIES_REVEAL_SCROLL_DP there) — shared so the icon finishes morphing
                    // in lockstep with the grid finishing its fade-in, not two independently
                    // tuned numbers that could drift out of sync.
                    val homeIconMorphScrollPx = with(LocalDensity.current) { MEMORIES_REVEAL_SCROLL_DP.dp.toPx() }

                    // Hoisted rather than let each tab screen create its own: real-time backdrop
                    // blur needs to set up a GPU render-effect pipeline (shader compile, capture
                    // buffers) the first time it runs. Each tab creating its own HazeState meant
                    // that pipeline was torn down and rebuilt from scratch on every single tab
                    // switch — a real, consistent stutter on every nav tap, not specific to any
                    // one screen. One shared instance keeps it alive across navigation.
                    val hazeState = rememberHazeState()

                    // Hoisted (rather than declared inside their respective page branches below)
                    // so they survive navigating into nested screens and back, and so they can
                    // be refreshed from elsewhere: FriendsViewModel after a friend is removed,
                    // HomeViewModel after a photo is sent (streaks can change on send, not just
                    // receive).
                    val friendsViewModel: FriendsViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { FriendsViewModel(friendRepository, localListCache) }
                        },
                    )
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                HomeViewModel(
                                    photoRepository,
                                    networkModule.tokenStore,
                                    userRepository,
                                    localListCache,
                                    initialHomeCache,
                                    onFeedLoaded = { items ->
                                        coroutineScope.launch { WidgetPhotoSync.sync(applicationContext, items) }
                                    },
                                )
                            }
                        },
                    )
                    // A NEW_PHOTO push updates the widget directly (see WidgetPhotoSync.syncFromPush
                    // in EmberFirebaseMessagingService, which needs no live app state at all) — this
                    // is the other half, letting a *live* HomeViewModel also pick up the change.
                    // loadFeed() here only ever updates syncedFeedItems (see HomeViewModel), never
                    // the visible browsing session directly, so a push landing while someone's
                    // mid-swipe through Home can't interrupt them — it just surfaces the "New
                    // memories available" indicator instead.
                    LaunchedEffect(Unit) {
                        emberApplication.newPhotoPushEvents.collect { homeViewModel.loadFeed() }
                    }
                    // Also hoisted, for the same reason: created here means its fetch starts as
                    // soon as the app opens, in the background, rather than only starting the
                    // moment the user first taps the Activity tab — that lazy-create pattern is
                    // what made Activity specifically feel slower to open than Home or Friends,
                    // whose ViewModels (and therefore their network calls) were already hoisted.
                    val activityViewModel: ActivityViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { ActivityViewModel(activityRepository, localListCache) }
                        },
                    )
                    // Camera is a page of the main pager now, not a screen only created on
                    // entry — hoisted alongside the other tab ViewModels so its state
                    // (specifically capturedFile, which gates whether swiping is allowed at all
                    // — see userScrollEnabled below) exists regardless of which page is current.
                    val cameraViewModel: CameraViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { CameraViewModel(friendRepository, photoRepository, subscriptionRepository) }
                        },
                    )

                    // Home, Friends, Camera, Activity and Settings are all pages of one
                    // full-screen pager now — not separate conditionally-composed screens, so a
                    // swipe can move between any of them, not just a tap on the nav dock. Home is
                    // simply always the first page; there's no "which page" decision to make any
                    // more now that Memories lives inline inside Home rather than needing its own
                    // slot in this pager.
                    val pagerState = rememberPagerState(initialPage = PAGE_HOME) { PAGE_COUNT }

                    // The recipient-picker friends fetch (limit=500 — see CameraViewModel's own
                    // comment) only actually needs to happen once the user reaches Camera, not
                    // at app launch regardless of whether they ever do this session. Fires once,
                    // the first time the pager actually settles there, however it got there
                    // (button tap or a manual swipe past Friends).
                    var hasLoadedCameraFriends by remember { mutableStateOf(false) }
                    LaunchedEffect(pagerState.settledPage) {
                        if (pagerState.settledPage == PAGE_CAMERA && !hasLoadedCameraFriends) {
                            hasLoadedCameraFriends = true
                            // Friends' own tab has very often already fetched this same list —
                            // reuse it instead of firing a second, mostly-redundant GET /friends
                            // when it's known to already be complete (hasMore == false). Falls
                            // back to CameraViewModel's own fetch whenever that isn't the case
                            // (Friends hasn't loaded yet this session, or genuinely has more than
                            // one page of friends).
                            // isLoading is checked too, not just hasMore/friends — a snapshot
                            // hydrated from FriendsViewModel's own local disk cache (see its
                            // init) can be an incomplete first page with hasMore still sitting at
                            // its pre-fetch default of false, until the real network fetch
                            // actually corrects it; isLoading only goes false once that real
                            // fetch has completed at least once.
                            if (!friendsViewModel.isLoading && !friendsViewModel.hasMore && friendsViewModel.friends.isNotEmpty()) {
                                cameraViewModel.provideFriends(friendsViewModel.friends)
                            } else {
                                cameraViewModel.loadFriends()
                            }
                        }
                    }

                    // Swiping away from Home shouldn't leave the rest of the app permanently
                    // blurred behind a focus state that page no longer shows.
                    LaunchedEffect(pagerState.settledPage) {
                        if (pagerState.settledPage != PAGE_HOME) isHomePhotoFocused = false
                    }
                    // Clears the nav-dock badge dot the moment Activity actually becomes the
                    // settled page — not on tab tap alone, so a swipe that passes through without
                    // stopping there doesn't count as having seen it.
                    LaunchedEffect(pagerState.settledPage) {
                        if (pagerState.settledPage == PAGE_ACTIVITY) activityViewModel.markSeen()
                    }
                    // cameraViewModel is scoped to this Activity, not recreated per visit —
                    // without discarding here, a capture the user swiped away from (rather than
                    // sent or explicitly retook) would still be sitting there in review,
                    // unreachable-looking-fresh, next time this page comes back into view. Used
                    // to be a button tap (Camera's own close button); Camera has no such button
                    // any more (it's a plain page of this pager, not a modal screen), so this is
                    // now the only place that cleanup happens.
                    LaunchedEffect(pagerState.settledPage) {
                        if (pagerState.settledPage != PAGE_CAMERA) cameraViewModel.discardCapture()
                    }
                    // Home's featured card has its own inner pager for cycling through photos —
                    // same swipe axis as this outer one, nested inside it. Compose doesn't always
                    // hand a gesture off cleanly between two pagers on the same axis, and the
                    // observed result is the drag ending with this pager stopped partway between
                    // two pages (both partially visible) instead of settled on either one. Rather
                    // than a fragile fix aimed at the gesture handoff itself, this is a general
                    // correction: whenever a drag ends and this pager isn't sitting exactly on a
                    // page, finish the job by animating the rest of the way to whichever one it
                    // was already closer to.
                    LaunchedEffect(pagerState.isScrollInProgress) {
                        if (!pagerState.isScrollInProgress && abs(pagerState.currentPageOffsetFraction) > 0.01f) {
                            val nearestPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .roundToInt()
                                .coerceIn(0, PAGE_COUNT - 1)
                            pagerState.animateScrollToPage(nearestPage)
                        }
                    }

                    // Shared by the on-screen back arrow (FriendProfileScreen's own onBack) and
                    // the system back gesture/button below — two genuinely separate code paths
                    // that both end this screen, previously only kept in sync by hand (a fix that
                    // only taught one of them to refresh the Friends list left the other, more
                    // commonly used one — a swipe/back-button — still showing stale data). Doesn't
                    // need to refresh anything itself: every action this screen can take
                    // (pin/unpin, remove, accept, decline) already pushes its own fresh result
                    // straight into friendsViewModel the instant it succeeds — see onPinChanged/
                    // onRemoved/onAccepted/onRejected below — so by the time this ever runs,
                    // friendsViewModel is already correct with no fetch of its own needed.
                    val onCloseFriendProfile = {
                        nestedScreen = null
                        selectedProfileSubject = null
                    }

                    // Swiping/pressing back should always retrace the last navigation step
                    // instead of falling through to the system default (which closes the app):
                    // close the recipient picker, then any nested screen, then return to Home
                    // before actually exiting. Camera has its own, higher-priority BackHandler
                    // (registered inside CameraScreen itself) for "back retakes instead of
                    // leaving" while a capture is pending — this one only ever fires once
                    // that's no longer the case.
                    BackHandler(
                        enabled = showRecipientPicker || nestedScreen != null || pagerState.currentPage != PAGE_HOME,
                    ) {
                        when {
                            showRecipientPicker -> showRecipientPicker = false
                            nestedScreen == NestedScreen.FRIEND_PROFILE -> onCloseFriendProfile()
                            nestedScreen != null -> nestedScreen = null
                            else -> coroutineScope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
                        }
                    }

                    // A tap on the nav dock (a tab, or the camera button) is a direct jump to
                    // that page — not the same gesture as a swipe, so it shouldn't play the
                    // same "scroll through every page in between" animation a swipe covering
                    // that same distance would. scrollToPage (not animateScrollToPage) snaps
                    // straight there; only an actual drag on screen animates through the pages
                    // it passes over.
                    val onNavigate: (NavDestination) -> Unit = { destination ->
                        nestedScreen = null
                        // Tapping Home while already on Home (very likely now, since Home is
                        // page 0) should behave like every other app's "tap the tab again" —
                        // scroll back to the top — rather than a no-op just because the pager
                        // page itself didn't need to change.
                        if (destination == NavDestination.HOME) {
                            coroutineScope.launch { homeScrollState.animateScrollTo(0) }
                        }
                        coroutineScope.launch { pagerState.scrollToPage(pageForDestination(destination)) }
                    }
                    val onCameraClick: () -> Unit = {
                        coroutineScope.launch { pagerState.scrollToPage(PAGE_CAMERA) }
                    }

                    when {
                        nestedScreen == NestedScreen.PROFILE -> {
                            val myProfileViewModel: MyProfileViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        MyProfileViewModel(
                                            userRepository,
                                            onProfileUpdated = { profile ->
                                                coroutineScope.launch { networkModule.tokenStore.saveDisplayName(profile.displayName) }
                                                homeViewModel.applyProfileUpdate(profile)
                                            },
                                        )
                                    }
                                },
                            )
                            MyProfileScreen(viewModel = myProfileViewModel, onClose = { nestedScreen = null })
                        }

                        nestedScreen == NestedScreen.THEME -> ThemeScreen(
                            viewModel = themeViewModel,
                            onPreview = { previewThemeKey = it },
                        )

                        nestedScreen == NestedScreen.GOLD -> EmberGoldScreen()

                        nestedScreen == NestedScreen.FIND_PEOPLE -> {
                            val findPeopleViewModel: FindPeopleViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { FindPeopleViewModel(friendRepository) }
                                },
                            )
                            FindPeopleScreen(
                                viewModel = findPeopleViewModel,
                                onResultClick = { result ->
                                    selectedProfileSubject = ProfileSubject.SearchResult(result)
                                    nestedScreen = NestedScreen.FRIEND_PROFILE
                                },
                            )
                        }

                        nestedScreen == NestedScreen.FRIEND_PROFILE && selectedProfileSubject != null -> {
                            val subject = selectedProfileSubject!!
                            // A ViewModel cached under a hand-built string key (tried userId
                            // alone, then subject-kind + userId) will always eventually collide,
                            // because the same person is legitimately revisited many times across
                            // a session as the relationship itself changes underneath — stranger
                            // to requested, requested to friend, friend to removed, removed back
                            // to stranger. Each of those is a genuinely new visit, but
                            // `viewModel(key = X)` only ever runs its factory on the *first*
                            // lookup for a given key and silently hands back that same stale
                            // instance to every later call with the same key, no matter what fresh
                            // subject was just passed in. The actual right scope for this
                            // ViewModel is "one visit to this screen," not "one person" or "one
                            // person in one relationship stage" — so it gets its own private
                            // ViewModelStore, created fresh whenever `subject` changes and cleared
                            // via the DisposableEffect below (canceling its viewModelScope too),
                            // the same lifetime a real back-stack entry would give it. This is the
                            // same pattern Jetpack Navigation uses internally per back-stack entry.
                            val profileViewModelStoreOwner = remember(subject) {
                                object : ViewModelStoreOwner {
                                    override val viewModelStore = ViewModelStore()
                                }
                            }
                            DisposableEffect(profileViewModelStoreOwner) {
                                onDispose { profileViewModelStoreOwner.viewModelStore.clear() }
                            }
                            val friendProfileViewModel: FriendProfileViewModel = viewModel(
                                viewModelStoreOwner = profileViewModelStoreOwner,
                                factory = viewModelFactory {
                                    initializer { FriendProfileViewModel(friendRepository, subject) }
                                },
                            )
                            FriendProfileScreen(
                                viewModel = friendProfileViewModel,
                                onBack = onCloseFriendProfile,
                                onSendPhotoClick = {
                                    // Sending from a specific friend's profile means that friend,
                                    // and only that friend, should end up selected in Camera — not
                                    // whatever was already selected (typically the pinned partner,
                                    // via CameraViewModel's own default). Safe unconditionally
                                    // because Send a photo only ever shows for
                                    // ProfileSubject.Friend, which always has a real friendId.
                                    cameraViewModel.setSelectedRecipients(setOf(subject.userId))
                                    // onCameraClick alone only scrolls the pager to Camera — it
                                    // doesn't close this nested screen, so without this the pager
                                    // was scrolling correctly underneath while FriendProfileScreen
                                    // kept covering it, making Camera invisible until back was
                                    // pressed (which calls onCloseFriendProfile itself).
                                    onCloseFriendProfile()
                                    onCameraClick()
                                },
                                // subject.friendshipId below is the same id this screen was
                                // opened with — stable for as long as the screen is open, so it's
                                // exactly the key each of these needs to update the Friends tab's
                                // own list in place, with no fetch of any kind.
                                onPinChanged = { updated -> friendsViewModel.applyUpdatedFriend(updated) },
                                onRemoved = {
                                    subject.friendshipId?.let { friendsViewModel.removeFriendLocally(it) }
                                    nestedScreen = null
                                    selectedProfileSubject = null
                                },
                                onAccepted = { newFriend ->
                                    friendsViewModel.addFriendLocally(newFriend)
                                    nestedScreen = null
                                    selectedProfileSubject = null
                                },
                                onRejected = {
                                    subject.friendshipId?.let { friendsViewModel.removePendingRequestLocally(it) }
                                    nestedScreen = null
                                    selectedProfileSubject = null
                                },
                            )
                        }

                        showRecipientPicker -> {
                            val recipientPickerViewModel: RecipientPickerViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        RecipientPickerViewModel(friendRepository, cameraViewModel.selectedRecipientIds)
                                    }
                                },
                            )
                            RecipientPickerScreen(
                                viewModel = recipientPickerViewModel,
                                onClose = { showRecipientPicker = false },
                                onConfirm = { ids ->
                                    cameraViewModel.setSelectedRecipients(ids)
                                    showRecipientPicker = false
                                },
                            )
                        }

                        else -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    // A photo mid-review/caption is easy to lose to an
                                    // accidental swipe — once one's been captured, swiping is
                                    // blocked entirely until it's sent or explicitly discarded.
                                    userScrollEnabled = cameraViewModel.capturedFile == null,
                                    // Default threshold needs the drag to cross ~50% of the
                                    // screen before release decides to commit to the next page —
                                    // a quick, short flick reasonably falls short of that and
                                    // snaps back instead of advancing. Lowered so a light flick is
                                    // enough, without needing a big deliberate drag all the way
                                    // across.
                                    flingBehavior = PagerDefaults.flingBehavior(
                                        state = pagerState,
                                        snapPositionalThreshold = 0.2f,
                                    ),
                                ) { page ->
                                    when (page) {
                                        PAGE_HOME -> HomeScreen(
                                            viewModel = homeViewModel,
                                            onCameraClick = onCameraClick,
                                            onAddFriendClick = { nestedScreen = NestedScreen.FIND_PEOPLE },
                                            onProfileClick = { nestedScreen = NestedScreen.PROFILE },
                                            hazeState = hazeState,
                                            isPhotoFocused = isHomePhotoFocused,
                                            onToggleFocus = { isHomePhotoFocused = !isHomePhotoFocused },
                                            onDismissFocus = { isHomePhotoFocused = false },
                                            scrollState = homeScrollState,
                                            isActive = pagerState.settledPage == PAGE_HOME,
                                        )

                                        PAGE_FRIENDS -> FriendsScreen(
                                            viewModel = friendsViewModel,
                                            onCameraClick = onCameraClick,
                                            onFindPeopleClick = { nestedScreen = NestedScreen.FIND_PEOPLE },
                                            onFriendClick = { friend ->
                                                selectedProfileSubject = ProfileSubject.Friend(friend)
                                                nestedScreen = NestedScreen.FRIEND_PROFILE
                                            },
                                            onPendingRequestClick = { request ->
                                                selectedProfileSubject = ProfileSubject.PendingRequest(request)
                                                nestedScreen = NestedScreen.FRIEND_PROFILE
                                            },
                                            hazeState = hazeState,
                                        )

                                        PAGE_CAMERA -> CameraScreen(
                                            viewModel = cameraViewModel,
                                            onOpenRecipientPicker = { showRecipientPicker = true },
                                            onUpgradeToGold = { nestedScreen = NestedScreen.GOLD },
                                            onSent = { response ->
                                                // Shows up in the Memories grid instantly,
                                                // rather than waiting on loadMemories()'s own
                                                // round trip below (which still runs, in the
                                                // background, to reconcile with the server).
                                                homeViewModel.prependMemory(
                                                    photoId = response.photoId,
                                                    photoUrl = response.url,
                                                    createdAt = response.createdAt,
                                                )
                                                coroutineScope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
                                                homeViewModel.loadFeed()
                                                homeViewModel.loadMemories()
                                            },
                                        )

                                        PAGE_ACTIVITY -> ActivityScreen(
                                            viewModel = activityViewModel,
                                            onCameraClick = onCameraClick,
                                            onNavigateToFriends = { onNavigate(NavDestination.FRIENDS) },
                                            hazeState = hazeState,
                                        )

                                        else -> {
                                            val notificationsEnabled by notificationPreferenceStore.enabled
                                                .collectAsState(initial = true)
                                            SettingsScreen(
                                                displayName = homeViewModel.userName,
                                                username = homeViewModel.username,
                                                profilePhotoUrl = homeViewModel.profilePhotoUrl,
                                                currentTheme = themeViewModel.selectedTheme,
                                                notificationsEnabled = notificationsEnabled,
                                                onNotificationsChange = { enabled ->
                                                    coroutineScope.launch { notificationPreferenceStore.save(enabled) }
                                                },
                                                onCameraClick = onCameraClick,
                                                onProfileClick = { nestedScreen = NestedScreen.PROFILE },
                                                onThemeClick = { nestedScreen = NestedScreen.THEME },
                                                onGoldClick = { nestedScreen = NestedScreen.GOLD },
                                                onSignOut = onSignOut,
                                                hazeState = hazeState,
                                            )
                                        }
                                    }
                                }

                                BottomNavDock(
                                    active = destinationForPage(pagerState.currentPage),
                                    onNavigate = onNavigate,
                                    onCameraClick = onCameraClick,
                                    showFriendsBadge = friendsViewModel.pendingRequests.isNotEmpty(),
                                    showActivityBadge = activityViewModel.hasNewActivity,
                                    // 0 = fully on Home (top of the scroll), 1 = fully on
                                    // Memories — driven by how far Home has been scrolled now,
                                    // not swipe position between two pages. Reads pagerState and
                                    // homeScrollState lazily (not as plain vals above) so these
                                    // continuously-changing values never force the scope
                                    // containing the pager itself to recompose on every scroll
                                    // frame; see BottomNavDock's own doc comment on this
                                    // parameter for why that matters. Any page other than Home
                                    // stays flatly at 0 (plain Home icon) — there's nothing to
                                    // morph towards there any more.
                                    homeIconProgress = {
                                        if (pagerState.currentPage == PAGE_HOME) {
                                            (homeScrollState.value / homeIconMorphScrollPx).coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        // Fades the whole dock out near the Camera page (its own
                                        // capture/review controls occupy similar bottom-of-screen
                                        // space) — a graphicsLayer lambda for the same reason as
                                        // homeIconProgress above: deferred to the draw phase so
                                        // it doesn't force a recomposition every swipe frame.
                                        .graphicsLayer {
                                            alpha = abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - PAGE_CAMERA)
                                                .coerceIn(0f, 1f)
                                        },
                                    hazeState = hazeState,
                                )
                            }
                        }
                    }
                }
            }
        }
        // The manifest's importantForAutofill="noExcludeDescendants" on this Activity doesn't
        // reach Compose content in practice — Compose's own ComposeView appears to mark itself
        // important for autofill regardless of what its ancestors declare. setContent() creates
        // and attaches that ComposeView synchronously as the sole child of the content root, so
        // right after it returns we can reach in and force the flag directly on the view that
        // actually matters, overriding whatever Compose set internally. This — not the manifest
        // attribute, not KeyboardType, not disableAutofillServices() (which silently no-ops
        // unless its one-time system dialog gets shown and accepted) — is what actually stops
        // Google Password Manager's "Save password?" prompt from firing on every login.
        (findViewById<ViewGroup>(android.R.id.content))?.getChildAt(0)
            ?.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
}
