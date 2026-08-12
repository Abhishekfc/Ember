package com.emigo.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Intent
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
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
import com.emigo.app.data.FirstPhotoPreloader
import com.emigo.app.data.local.LocalListCache
import com.emigo.app.data.remote.dto.FeedItem
import com.emigo.app.data.remote.dto.MemoryPhotoDto
import com.emigo.app.data.remote.dto.UserProfileDto
import com.emigo.app.ui.activity.ActivityScreen
import com.emigo.app.ui.activity.ActivityViewModel
import com.emigo.app.ui.auth.AuthPalette
import com.emigo.app.ui.auth.LoginScreen
import com.emigo.app.ui.auth.LoginViewModel
import com.emigo.app.ui.camera.CameraScreen
import com.emigo.app.ui.camera.CameraViewModel
import com.emigo.app.ui.camera.RecipientPickerScreen
import com.emigo.app.ui.camera.RecipientPickerViewModel
import com.emigo.app.ui.camera.SentPhotosScreen
import com.emigo.app.ui.camera.SentPhotosViewModel
import com.emigo.app.ui.components.BottomNavDock
import com.emigo.app.ui.components.FALLBACK_NAV_DOCK_HEIGHT_DP
import com.emigo.app.ui.components.LocalNavDockHeight
import com.emigo.app.ui.components.NavDestination
import com.emigo.app.ui.friends.FindPeopleScreen
import com.emigo.app.ui.friends.FindPeopleViewModel
import com.emigo.app.ui.friends.FriendProfileScreen
import com.emigo.app.ui.friends.FriendProfileViewModel
import com.emigo.app.ui.friends.FriendsScreen
import com.emigo.app.ui.friends.FriendsViewModel
import com.emigo.app.ui.friends.ProfileSubject
import com.emigo.app.ui.home.HomeScreen
import com.emigo.app.ui.home.HomeViewModel
import com.emigo.app.ui.home.InitialHomeCache
import com.emigo.app.ui.home.MemoriesTabScreen
import com.emigo.app.ui.profile.MyProfileScreen
import com.emigo.app.ui.profile.MyProfileViewModel
import com.emigo.app.ui.settings.AppIconKey
import com.emigo.app.ui.settings.AppIconScreen
import com.emigo.app.ui.settings.AppIconSwitcher
import com.emigo.app.ui.settings.AppIconViewModel
import com.emigo.app.ui.settings.BlockedUsersScreen
import com.emigo.app.ui.settings.BlockedUsersViewModel
import com.emigo.app.ui.settings.EmberGoldScreen
import com.emigo.app.ui.settings.OtherSettingsScreen
import com.emigo.app.ui.settings.SettingsScreen
import com.emigo.app.ui.settings.WidgetSettingsScreen
import com.emigo.app.ui.settings.WidgetSettingsViewModel
import com.emigo.app.ui.theme.EmberAppTheme
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.ThemeKey
import com.emigo.app.ui.theme.ThemeScreen
import com.emigo.app.ui.theme.ThemeViewModel
import com.emigo.app.widget.EmberWidget
import com.emigo.app.widget.WidgetPhotoStore
import com.emigo.app.widget.WidgetPhotoSync
import com.emigo.app.widget.WidgetPreferenceStore
import com.emigo.app.widget.WidgetUpdateWorker
import androidx.glance.appwidget.updateAll
import com.google.firebase.messaging.FirebaseMessaging
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** Screens reached from within a tab (Settings -> Theme, Friends -> Find People / Friend
 * Profile) rather than from the bottom nav tabs directly. Kept separate from the current page
 * so back navigation can pop just the nested screen without losing which page you were on.
 * Camera is NOT one of these any more — it's a swipeable page of the main pager, same as Home
 * or Friends, not a modal reached from a button. Activity joined this list (rather than staying
 * a pager page) once it moved out of the bottom nav dock into a bell icon in Home's own header —
 * see NavDestination's own doc comment for the full reasoning. */
private enum class NestedScreen { THEME, APP_ICON, FIND_PEOPLE, FRIEND_PROFILE, PROFILE, GOLD, WIDGET_SETTINGS, BLOCKED_USERS, OTHER_SETTINGS, SENT_PHOTOS, ACTIVITY }

/** The unified pager's page order — left to right, matching the bottom nav's own visual layout
 * (Memories, Home, [Camera in the center], Friends, Settings). Home sits immediately next to
 * Camera on purpose, not at either end: the app opens on Camera (see PAGE_CAMERA below being the
 * pager's initialPage), and a single swipe away from it needs to land directly on Home, not
 * Memories — Memories is one swipe further out instead. Activity has no page of its own any more
 * (see NestedScreen/NavDestination's own doc comments) — swiping past Friends or Settings never
 * lands on it, the way it briefly could when it was still page 3 here. */
// Intent extras a notification's action button (or its own body tap) can carry to route
// straight to a specific in-app action once MainActivity is showing — currently only the
// streak-restore notification's "Restore streak" button uses these (see
// EmberFirebaseMessagingService.showStreakBrokenNotification, the only place that sets them).
const val EXTRA_NOTIFICATION_ACTION = "notification_action"
const val EXTRA_STREAK_FRIENDSHIP_ID = "streak_friendship_id"
const val NOTIFICATION_ACTION_RESTORE_STREAK = "restore_streak"

private const val PAGE_MEMORIES = 0
private const val PAGE_HOME = 1
private const val PAGE_CAMERA = 2
private const val PAGE_FRIENDS = 3
private const val PAGE_SETTINGS = 4
private const val PAGE_COUNT = 5

private fun pageForDestination(destination: NavDestination): Int = when (destination) {
    NavDestination.MEMORIES -> PAGE_MEMORIES
    NavDestination.HOME -> PAGE_HOME
    NavDestination.FRIENDS -> PAGE_FRIENDS
    NavDestination.SETTINGS -> PAGE_SETTINGS
}

/** Which nav-dock tab should read as "active" for a given page — Camera doesn't correspond to
 * any tab (its icon fades out entirely near that page instead, see the dock's own alpha
 * graphicsLayer below), so it falls back to Home. */
private fun destinationForPage(page: Int): NavDestination = when (page) {
    PAGE_MEMORIES -> NavDestination.MEMORIES
    PAGE_HOME -> NavDestination.HOME
    PAGE_FRIENDS -> NavDestination.FRIENDS
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
    private val safetyRepository get() = emberApplication.safetyRepository
    private val themePreferenceStore get() = emberApplication.themePreferenceStore
    private val appIconPreferenceStore get() = emberApplication.appIconPreferenceStore
    private val notificationPreferenceStore get() = emberApplication.notificationPreferenceStore
    private val localListCache get() = emberApplication.localListCache

    // Compose state (not a plain var) so the LaunchedEffect further down in setContent's own
    // composable tree actually re-runs when this changes — set from whatever intent the Activity
    // was most recently (re)launched with (onCreate for a cold start, onNewIntent for singleTask
    // reuse while already running), and nulled back out once that effect has acted on it, so the
    // same notification tap can't be processed twice across a recomposition.
    private var pendingNotificationIntent by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationIntent = intent
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
        // Read before the first composition, exactly like initialHomeCache below it, so the very
        // first frame already knows whether to draw Home or Login. This used to happen in a
        // LaunchedEffect instead, which by definition runs *after* that first frame — so every
        // cold start rendered one empty placeholder frame first, and only then the real UI. That
        // gap was invisible while every theme's background was a flat gradient (the placeholder
        // painted the identical background), but a theme with an image backdrop made it obvious:
        // the backdrop appeared alone, then everything else arrived a beat later.
        val hasSavedSession = runBlocking { networkModule.tokenStore.currentToken() != null }
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
                    initializer { ThemeViewModel(themePreferenceStore, subscriptionRepository) }
                },
            )
            val appIconViewModel: AppIconViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { AppIconViewModel(appIconPreferenceStore, subscriptionRepository) }
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
                // Seeded from the synchronous read in onCreate, so there's no "we don't know yet"
                // state to render a placeholder for — a returning user gets Home on frame one and
                // a signed-out user gets Login on frame one.
                var authenticated by remember { mutableStateOf(hasSavedSession) }
                var nestedScreen by remember { mutableStateOf<NestedScreen?>(null) }
                var selectedProfileSubject by remember { mutableStateOf<ProfileSubject?>(null) }
                // Where closing the friend profile should land, since `nestedScreen` holds one
                // screen rather than a back stack: a profile opened from another nested screen
                // (Activity, Find People) has to return *there*, not fall through to the pager
                // and silently discard the screen it was opened from — leaving Find People, for
                // one, meant losing the search results you'd just typed. Null means "opened from
                // a pager tab", the plain case. Every site that opens a profile sets this
                // explicitly, so a value can never linger from a previous, unrelated visit.
                var friendProfileReturnTo by remember { mutableStateOf<NestedScreen?>(null) }
                val appContext = LocalContext.current

                // One shared instance — read reactively for the Settings badge below, written
                // once per session by the Gold-status LaunchedEffect further down, and reused by
                // onSignOut's own cleanup, rather than each site constructing its own.
                val widgetPreferenceStore = remember { WidgetPreferenceStore(applicationContext) }
                // Mirrors what gets written into widgetPreferenceStore's own cached Gold status
                // (see that LaunchedEffect's doc comment) — kept as a plain Compose state too so
                // Settings can show a real "Gold"/"Free" badge without re-reading DataStore itself.
                var isGoldMember by remember { mutableStateOf(false) }
                val widgetFeaturedFriendIds by widgetPreferenceStore.featuredFriendIds.collectAsState(initial = emptySet())

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

                // Shared by the manual "Sign out" button in Settings and by the automatic
                // handler below for an expired/invalid token (a 401 on an authenticated
                // request) — both need to land the user back on a clean login screen.
                val onSignOut = {
                    // Detach this device from the account's push list *before* the token that
                    // authenticates that call is cleared — see AuthRepository.unregisterDeviceToken
                    // for why leaving it attached kept delivering the previous account's photo
                    // notifications (friend names included) to a signed-out phone. Sequential in
                    // one coroutine for exactly that ordering; every other cleanup below is
                    // independent and stays parallel.
                    coroutineScope.launch {
                        val fcmToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                        if (fcmToken != null) authRepository.unregisterDeviceToken(fcmToken)
                        networkModule.tokenStore.clear()
                    }
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
                    subscriptionRepository.clearCache()
                    // Persisted to disk (see SubscriptionRepository.lastKnownIsActive), so it
                    // needs its own explicit clear here too — otherwise a different account
                    // signing in offline on this device would inherit the previous account's
                    // last-confirmed Gold status instead of defaulting to false like any other
                    // brand-new session.
                    coroutineScope.launch { subscriptionRepository.clearLastKnownStatus() }
                    // Theme has no backend representation — it's a purely local, device-scoped
                    // preference (see ThemePreferenceStore.clear's own doc comment) — without
                    // this, a different account signing in on this device would inherit whatever
                    // theme (Gold-gated ones included) the previous account had chosen.
                    coroutineScope.launch { themePreferenceStore.clear() }
                    // Disk-level clear above has no effect on this same-instance-for-the-whole-
                    // session ViewModel's own already-resolved in-memory state — see
                    // ThemeViewModel.reset's own doc comment for why that's the actual reason a
                    // Gold-gated theme kept visibly applying after signing out.
                    themeViewModel.reset()
                    // Same reasoning as the theme cleanup right above, plus one more step a
                    // theme choice never needed: the launcher icon is a real OS-level setting
                    // (see AppIconSwitcher), not just in-memory app state, so it would otherwise
                    // keep showing whatever a Gold subscriber last chose indefinitely on this
                    // device even after signing out of their account.
                    coroutineScope.launch {
                        appIconPreferenceStore.clear()
                        AppIconSwitcher.apply(applicationContext, AppIconKey.DEFAULT)
                    }
                    coroutineScope.launch { notificationPreferenceStore.clear() }
                    // The widget reads its cached photo (and, for a Gold subscriber, their
                    // featured-friend choice + cached Gold status) independent of sign-in state —
                    // without this, a friend's private photo (and their name), or a previous
                    // account's widget customization, keeps applying indefinitely after "signing
                    // out."
                    coroutineScope.launch {
                        WidgetPhotoStore(applicationContext).clear()
                        widgetPreferenceStore.clear()
                        EmberWidget().updateAll(applicationContext)
                    }
                    // Same reasoning as the widget's cached photo right above, for the much larger
                    // store: Coil keeps every photo this account viewed — friends' photos, their
                    // profile pictures — in an on-disk cache under the app's own directory, and
                    // that survived sign-out untouched. Clearing the widget's single cached photo
                    // as private data while leaving the full browsing history of decoded photos on
                    // disk was inconsistent; both belong to the account that just signed out.
                    // Only costs a re-download of whatever is looked at again after signing in.
                    SingletonImageLoader.get(applicationContext).let { loader ->
                        loader.memoryCache?.clear()
                        coroutineScope.launch(Dispatchers.IO) { loader.diskCache?.clear() }
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

                // Refreshes the widget's locally-cached Gold status once per authenticated
                // session — the same one-shot-on-open shape CameraViewModel/ThemeViewModel
                // already use for their own Gold checks, not a new pattern. WidgetPhotoSync reads
                // this cached value instead of checking live on every sync (including the 6h
                // background worker and incoming pushes), so a lapsed subscription self-heals the
                // next time the app is opened rather than needing a network call on every widget
                // update. Shares SubscriptionRepository's own TTL cache with those other checks,
                // so this rarely costs a genuinely new network round trip on top of them.
                LaunchedEffect(authenticated) {
                    if (authenticated) {
                        // isGoldMemberOrLastKnown(), not a bare getStatus() read — opening the app
                        // offline must never overwrite this cache with false for a genuine
                        // subscriber just because the live check couldn't reach the server (see
                        // SubscriptionRepository's own doc comment on that function).
                        isGoldMember = subscriptionRepository.isGoldMemberOrLastKnown()
                        widgetPreferenceStore.setCachedIsGoldMember(isGoldMember)
                    }
                }

                if (!authenticated) {
                    LoginScreen(
                        viewModel = loginViewModel,
                        onAuthenticated = {
                            authenticated = true
                            // Re-resolves this newly signed-in account's own saved theme + real
                            // Gold status — see ThemeViewModel.reload's own doc comment for why
                            // this doesn't just happen on its own otherwise.
                            themeViewModel.reload()
                        },
                    )
                } else {
                    var showRecipientPicker by remember { mutableStateOf(false) }

                    // Both of the app's core runtime permissions, asked once together right after
                    // sign-in rather than each at its own first use. Camera used to be requested
                    // only on arriving at the Camera tab, which meant the app's central action
                    // was gated behind a prompt at the exact moment someone wanted to use it;
                    // notifications are needed before the first photo arrives, not after. Asked
                    // after sign-in (not on the login screen) so nobody is prompted before
                    // they've committed to the app at all.
                    //
                    // CameraScreen keeps its own check and launcher regardless — this is a
                    // convenience, not a guarantee, and someone who declines here (or revokes
                    // later in system settings) still needs a way to grant it in context.
                    //
                    // WRITE_EXTERNAL_STORAGE is deliberately not requested here: it only exists
                    // for saving a photo to the gallery on Android 9 and below (see the manifest's
                    // maxSdkVersion), so asking every user up front for something most never do,
                    // on an OS version most aren't running, would cost a denial for nothing. It
                    // stays where it is, asked at the moment someone actually taps save.
                    val startupPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) {}
                    LaunchedEffect(Unit) {
                        val wanted = buildList {
                            add(Manifest.permission.CAMERA)
                            // POST_NOTIFICATIONS simply does not exist before Android 13 —
                            // requesting it there throws rather than being ignored.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.filter {
                            // Only ask for what isn't already granted, so a returning user isn't
                            // re-prompted on every launch.
                            ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (wanted.isNotEmpty()) startupPermissionLauncher.launch(wanted.toTypedArray())
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
                    // Hoisted for a different reason than Home's above: FriendsScreen itself is
                    // fully disposed while a friend's profile is open (the nestedScreen `when`
                    // block below only ever composes one branch at a time), so a scroll position
                    // FriendsScreen owned itself would silently reset to the top on every return
                    // from a profile — remembering it here, outside that `when`, is what lets it
                    // survive that round trip instead.
                    val friendsListState = rememberLazyListState()
                    // Same reasoning as friendsListState just above, for a much more visible bug:
                    // this used to be remembered inside the `else` branch below, alongside the
                    // HorizontalPager itself — which meant every return from *any* nested screen
                    // (not just a friend's profile) reset it to FALLBACK_NAV_DOCK_HEIGHT_DP for
                    // one frame before BottomNavDock's own onSizeChanged corrected it back to the
                    // real measured value. Every screen reserves bottom space equal to this value
                    // (see LocalNavDockHeight), so that one-frame guess-then-correct made the
                    // whole page's content visibly shift as the reserved space changed size —
                    // reported as "the complete page does like a vibrate... moves a little from
                    // bottom to top." Hoisting it here means it's set once, correctly, and never
                    // guessed again for the rest of the session.
                    var navDockHeight by remember { mutableStateOf(FALLBACK_NAV_DOCK_HEIGHT_DP) }

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
                            initializer {
                                FriendsViewModel(
                                    friendRepository,
                                    localListCache,
                                    subscriptionRepository,
                                    onFriendsChanged = { emberApplication.notifyFriendsChanged() },
                                )
                            }
                        },
                    )
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                HomeViewModel(
                                    photoRepository,
                                    networkModule.tokenStore,
                                    userRepository,
                                    friendRepository,
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
                    // FriendsViewModel otherwise only ever refetches at app start or an explicit
                    // pull-to-refresh — nothing was re-syncing it when a friend's photo actually
                    // arrived, so "Last sent"/streak on the Friends tab could sit stale for as
                    // long as someone simply never happened to pull-to-refresh that screen,
                    // even with the app fully online the whole time. A received photo is exactly
                    // the event that changes those two fields, so it gets the same treatment as
                    // Home's own feed above.
                    LaunchedEffect(Unit) {
                        emberApplication.newPhotoPushEvents.collect { friendsViewModel.refreshSilently() }
                    }
                    // Same bridge, the other direction: a queued send (see PendingSendWorker)
                    // finishing is *my own* new photo, so both Feed and Memories need refreshing
                    // — unlike the push case above, which only ever needs Feed.
                    LaunchedEffect(Unit) {
                        emberApplication.photoSendCompletedEvents.collect {
                            homeViewModel.loadFeed()
                            homeViewModel.loadMemories()
                            // My own send is just as much a streak/last-activity change for
                            // whoever I sent it to as receiving one from them is above.
                            friendsViewModel.refreshSilently()
                        }
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
                            initializer { CameraViewModel(friendRepository, photoRepository, subscriptionRepository, localListCache) }
                        },
                    )

                    // Camera's own recipient list (see hasLoadedCameraFriends below) is fetched
                    // exactly once per session and never on its own initiative afterward — without
                    // this, a friend request accepted anywhere kept being invisible in Camera's
                    // picker until the app was restarted, since nothing ever told this long-lived
                    // ViewModel its own copy had gone stale.
                    LaunchedEffect(Unit) {
                        emberApplication.friendsChangedEvents.collect { cameraViewModel.loadFriends() }
                    }
                    // Flips the outbox button's animation from SENDING to its filled checkmark —
                    // see CameraViewModel.markSendComplete's own doc comment for why this coarse,
                    // not-photo-specific signal (shared with the Feed/Memories/Friends refresh
                    // above) is good enough here. A separate collector, not folded into that one,
                    // purely because cameraViewModel isn't declared yet at that earlier point in
                    // this function.
                    LaunchedEffect(Unit) {
                        emberApplication.photoSendCompletedEvents.collect { cameraViewModel.markSendComplete() }
                    }

                    // Home, Friends, Camera, Activity and Settings are all pages of one
                    // full-screen pager now — not separate conditionally-composed screens, so a
                    // swipe can move between any of them, not just a tap on the nav dock. Opens on
                    // Camera (Snapchat/Locket-style: the app's default view is "take a photo," not
                    // a feed) — Home is one swipe away from it either direction is irrelevant here
                    // since PAGE_HOME sits immediately adjacent to PAGE_CAMERA either way (see
                    // PAGE_HOME's own doc comment for why that adjacency is deliberate).
                    val pagerState = rememberPagerState(initialPage = PAGE_CAMERA) { PAGE_COUNT }

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
                    // Clears the header bell's badge the moment Activity actually becomes the
                    // shown nested screen — not on the bell tap alone, matching the exact same
                    // "only counts once it's actually visible" timing this had back when Activity
                    // was still a pager page keyed on settledPage instead of nestedScreen.
                    LaunchedEffect(nestedScreen) {
                        if (nestedScreen == NestedScreen.ACTIVITY) activityViewModel.markSeen()
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
                        nestedScreen = friendProfileReturnTo
                        friendProfileReturnTo = null
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

                    /** The profile subject behind an activity row's actor, or null if they can't
                     * be placed. Pending requests are checked first on purpose: someone who has a
                     * request still waiting must open as a PendingRequest so the profile offers
                     * accept/decline, which the plain Friend case has no reason to show. */
                    val resolveActorSubject: (String) -> ProfileSubject? = { actorId ->
                        friendsViewModel.pendingRequests.firstOrNull { it.requesterId == actorId }
                            ?.let { ProfileSubject.PendingRequest(it) }
                            ?: friendsViewModel.friends.firstOrNull { it.friendId == actorId }
                                ?.let { ProfileSubject.Friend(it) }
                    }

                    // Handles a tap on the streak-broken notification's "Restore streak" action
                    // (see EmberFirebaseMessagingService.showStreakBrokenNotification, the only
                    // place that sets these extras) — the same Gold-or-restore branch
                    // FriendsScreen's own restore pill makes, just reached from outside the
                    // Compose tree instead of a button tap. Keyed on the intent itself (not just
                    // `Unit`) so a second, different notification tap while this effect's scope
                    // is still alive correctly restarts it rather than being ignored as "already
                    // running". Nulls the pending intent back out once handled (or once
                    // determined to be irrelevant) so recomposition can't replay the same action
                    // twice.
                    LaunchedEffect(pendingNotificationIntent) {
                        val intent = pendingNotificationIntent ?: return@LaunchedEffect
                        if (intent.getStringExtra(EXTRA_NOTIFICATION_ACTION) == NOTIFICATION_ACTION_RESTORE_STREAK) {
                            val friendshipId = intent.getStringExtra(EXTRA_STREAK_FRIENDSHIP_ID)
                            if (friendshipId != null) {
                                if (subscriptionRepository.isGoldMemberOrLastKnown()) {
                                    friendsViewModel.restoreStreak(friendshipId)
                                    onNavigate(NavDestination.FRIENDS)
                                } else {
                                    nestedScreen = NestedScreen.GOLD
                                }
                            }
                        }
                        pendingNotificationIntent = null
                    }

                    // Wraps the whole nested-screen `when` (and the Gold overlay below it) so
                    // Gold can render on top of whatever the `when` currently shows, rather than
                    // being one more mutually-exclusive branch inside it.
                    Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        nestedScreen == NestedScreen.PROFILE -> {
                            val myProfileViewModel: MyProfileViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        MyProfileViewModel(
                                            userRepository,
                                            localListCache,
                                            initialProfile = initialHomeCache.profile,
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
                            onBack = { nestedScreen = null },
                            onPreview = { previewThemeKey = it },
                            onUpgradeToGold = { nestedScreen = NestedScreen.GOLD },
                        )

                        nestedScreen == NestedScreen.APP_ICON -> AppIconScreen(
                            viewModel = appIconViewModel,
                            onBack = { nestedScreen = null },
                            onUpgradeToGold = { nestedScreen = NestedScreen.GOLD },
                        )

                        // NestedScreen.GOLD is deliberately not a branch here — see the Box/
                        // AnimatedVisibility wrapping this whole `when`, below.

                        // Activity moved here from being pager page PAGE_ACTIVITY — reached via
                        // the bell icon in Home's own header now (see HomeScreen's onActivityClick)
                        // rather than a dock tab/swipe. onCameraClick and onNavigateToFriends both
                        // need to close this nested screen *and* move the pager, the same two-step
                        // pattern FriendProfileScreen's own onSendPhotoClick already uses — closing
                        // alone would leave the pager sitting on whatever page it already was on,
                        // underneath this screen, rather than actually navigating anywhere.
                        nestedScreen == NestedScreen.ACTIVITY -> ActivityScreen(
                            viewModel = activityViewModel,
                            onCameraClick = {
                                nestedScreen = null
                                onCameraClick()
                            },
                            onNavigateToFriends = {
                                nestedScreen = null
                                onNavigate(NavDestination.FRIENDS)
                            },
                            // Both go through the same resolver, so "is this tappable" and "what
                            // does it open" can never disagree.
                            canOpenActorProfile = { actorId -> resolveActorSubject(actorId) != null },
                            onOpenActorProfile = { actorId ->
                                resolveActorSubject(actorId)?.let { subject ->
                                    selectedProfileSubject = subject
                                    friendProfileReturnTo = NestedScreen.ACTIVITY
                                    nestedScreen = NestedScreen.FRIEND_PROFILE
                                }
                            },
                            hazeState = hazeState,
                        )

                        nestedScreen == NestedScreen.WIDGET_SETTINGS -> {
                            val widgetSettingsViewModel: WidgetSettingsViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        WidgetSettingsViewModel(friendRepository, subscriptionRepository, widgetPreferenceStore)
                                    }
                                },
                            )
                            WidgetSettingsScreen(
                                viewModel = widgetSettingsViewModel,
                                onClose = { nestedScreen = null },
                                onUpgradeToGold = { nestedScreen = NestedScreen.GOLD },
                            )
                        }

                        nestedScreen == NestedScreen.BLOCKED_USERS -> {
                            val blockedUsersViewModel: BlockedUsersViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { BlockedUsersViewModel(safetyRepository) }
                                },
                            )
                            BlockedUsersScreen(
                                viewModel = blockedUsersViewModel,
                                onClose = { nestedScreen = null },
                            )
                        }

                        nestedScreen == NestedScreen.SENT_PHOTOS -> {
                            val sentPhotosViewModel: SentPhotosViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { SentPhotosViewModel(photoRepository) }
                                },
                            )
                            SentPhotosScreen(
                                viewModel = sentPhotosViewModel,
                                onBack = { nestedScreen = null },
                            )
                        }

                        nestedScreen == NestedScreen.OTHER_SETTINGS -> {
                            OtherSettingsScreen(
                                onClose = { nestedScreen = null },
                                onDeleteAccount = { userRepository.deleteAccount() },
                                // Same local cleanup + return-to-login as a manual sign-out —
                                // there's no account left for any of that cached state to belong
                                // to either.
                                onAccountDeleted = onSignOut,
                            )
                        }

                        nestedScreen == NestedScreen.FIND_PEOPLE -> {
                            val findPeopleViewModel: FindPeopleViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { FindPeopleViewModel(friendRepository) }
                                },
                            )
                            FindPeopleScreen(
                                viewModel = findPeopleViewModel,
                                onBack = { nestedScreen = null },
                                onResultClick = { result ->
                                    selectedProfileSubject = ProfileSubject.SearchResult(result)
                                    friendProfileReturnTo = NestedScreen.FIND_PEOPLE
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
                                    initializer { FriendProfileViewModel(friendRepository, safetyRepository, subject) }
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
                                    emberApplication.notifyFriendsChanged()
                                    nestedScreen = null
                                    selectedProfileSubject = null
                                },
                                onAccepted = { newFriend ->
                                    friendsViewModel.addFriendLocally(newFriend)
                                    emberApplication.notifyFriendsChanged()
                                    nestedScreen = null
                                    selectedProfileSubject = null
                                },
                                onRejected = {
                                    subject.friendshipId?.let { friendsViewModel.removePendingRequestLocally(it) }
                                    nestedScreen = null
                                    selectedProfileSubject = null
                                },
                                onBlocked = {
                                    // Same local-list update as onRemoved above — blocking also
                                    // deletes any existing friendship server-side (BlockService's
                                    // own doc comment), and a pending request between the two is
                                    // just as invalid to keep showing once blocked.
                                    subject.friendshipId?.let {
                                        friendsViewModel.removeFriendLocally(it)
                                        friendsViewModel.removePendingRequestLocally(it)
                                    }
                                    emberApplication.notifyFriendsChanged()
                                    nestedScreen = null
                                    selectedProfileSubject = null
                                },
                            )
                        }

                        showRecipientPicker -> {
                            val recipientPickerViewModel: RecipientPickerViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        RecipientPickerViewModel(friendRepository, localListCache, cameraViewModel.selectedRecipientIds)
                                    }
                                },
                            )
                            // Same reasoning as cameraViewModel's own collector above — this
                            // screen's ViewModel can be an existing (not freshly re-fetched)
                            // instance depending on how Compose's own viewModel() store reuse
                            // lands, so it needs the same "tell me if I've gone stale" signal
                            // rather than assuming its very first fetch is still good.
                            LaunchedEffect(Unit) {
                                emberApplication.friendsChangedEvents.collect { recipientPickerViewModel.loadFriends() }
                            }
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
                                // navDockHeight itself is hoisted above this whole `when` now —
                                // see its own doc comment there for why. Screens read it via
                                // LocalNavDockHeight instead of a fixed dp constant, which is what
                                // originally left the Settings screen's Log out button partly
                                // covered by the dock on at least one real device.
                                CompositionLocalProvider(LocalNavDockHeight provides navDockHeight) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    // Keeps the immediate neighbours of the current tab composed
                                    // instead of tearing them down the moment you swipe away.
                                    //
                                    // This is what fixes Home's featured card briefly showing the
                                    // previous photo every time you come back to it. Rebuilding
                                    // Home from scratch recreates its card pager, and while that
                                    // pager restores its page number immediately, the scroll
                                    // offset it needs to actually *show* that page is only applied
                                    // on the following frame — so for one frame it paints the page
                                    // before it. Verified via logging: the page index was already
                                    // correct on return every single time, which is why nothing
                                    // that adjusted the page number ever helped. Not rebuilding
                                    // the screen at all removes the wrong frame entirely, rather
                                    // than trying to correct it after it's been drawn.
                                    beyondViewportPageCount = 1,
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
                                        PAGE_MEMORIES -> MemoriesTabScreen(
                                            viewModel = homeViewModel,
                                            onCameraClick = onCameraClick,
                                            hazeState = hazeState,
                                        )

                                        PAGE_HOME -> HomeScreen(
                                            viewModel = homeViewModel,
                                            onCameraClick = onCameraClick,
                                            onAddFriendClick = { nestedScreen = NestedScreen.FIND_PEOPLE },
                                            onProfileClick = { nestedScreen = NestedScreen.PROFILE },
                                            onActivityClick = { nestedScreen = NestedScreen.ACTIVITY },
                                            activityBadgeCount = activityViewModel.newActivityCount,
                                            hazeState = hazeState,
                                            isPhotoFocused = isHomePhotoFocused,
                                            onToggleFocus = { isHomePhotoFocused = !isHomePhotoFocused },
                                            onDismissFocus = { isHomePhotoFocused = false },
                                            scrollState = homeScrollState,
                                            isActive = pagerState.settledPage == PAGE_HOME,
                                            hasSharedRecently = cameraViewModel.lastSentPhotoUrl != null,
                                        )

                                        PAGE_FRIENDS -> FriendsScreen(
                                            viewModel = friendsViewModel,
                                            onCameraClick = onCameraClick,
                                            onFindPeopleClick = { nestedScreen = NestedScreen.FIND_PEOPLE },
                                            onFriendClick = { friend ->
                                                selectedProfileSubject = ProfileSubject.Friend(friend)
                                                friendProfileReturnTo = null
                                                nestedScreen = NestedScreen.FRIEND_PROFILE
                                            },
                                            onPendingRequestClick = { request ->
                                                selectedProfileSubject = ProfileSubject.PendingRequest(request)
                                                friendProfileReturnTo = null
                                                nestedScreen = NestedScreen.FRIEND_PROFILE
                                            },
                                            onUpgradeToGold = { nestedScreen = NestedScreen.GOLD },
                                            hazeState = hazeState,
                                            listState = friendsListState,
                                        )

                                        PAGE_CAMERA -> CameraScreen(
                                            viewModel = cameraViewModel,
                                            onOpenRecipientPicker = { showRecipientPicker = true },
                                            onUpgradeToGold = { nestedScreen = NestedScreen.GOLD },
                                            onOpenSentPhotos = { nestedScreen = NestedScreen.SENT_PHOTOS },
                                            onSent = {
                                                // Fires the instant the photo is queued, not once
                                                // it's actually uploaded — PendingSendWorker now
                                                // sends it in the background (possibly much later,
                                                // if there's no connectivity yet). Deliberately
                                                // stays on Camera rather than navigating to Home —
                                                // CameraScreen's own header shows Sending/Sent
                                                // directly, so there's no need to leave the page
                                                // to see the outcome. These two still refresh right
                                                // away regardless, quietly in the background, so
                                                // Home/Memories are ready with the real photo by
                                                // the time the user does swipe over there.
                                                homeViewModel.loadFeed()
                                                homeViewModel.loadMemories()
                                            },
                                        )

                                        else -> {
                                            val notificationsEnabled by notificationPreferenceStore.enabled
                                                .collectAsState(initial = true)
                                            SettingsScreen(
                                                displayName = homeViewModel.userName,
                                                username = homeViewModel.username,
                                                profilePhotoUrl = homeViewModel.profilePhotoUrl,
                                                currentTheme = themeViewModel.selectedTheme,
                                                currentAppIcon = appIconViewModel.selectedIcon,
                                                isGoldMember = isGoldMember,
                                                widgetBadge = if (widgetFeaturedFriendIds.isEmpty()) {
                                                    "Anyone"
                                                } else {
                                                    "${widgetFeaturedFriendIds.size} friend${if (widgetFeaturedFriendIds.size == 1) "" else "s"}"
                                                },
                                                notificationsEnabled = notificationsEnabled,
                                                onNotificationsChange = { enabled ->
                                                    coroutineScope.launch { notificationPreferenceStore.save(enabled) }
                                                },
                                                onCameraClick = onCameraClick,
                                                onProfileClick = { nestedScreen = NestedScreen.PROFILE },
                                                onThemeClick = { nestedScreen = NestedScreen.THEME },
                                                onAppIconClick = { nestedScreen = NestedScreen.APP_ICON },
                                                onGoldClick = { nestedScreen = NestedScreen.GOLD },
                                                onWidgetClick = { nestedScreen = NestedScreen.WIDGET_SETTINGS },
                                                onBlockedUsersClick = { nestedScreen = NestedScreen.BLOCKED_USERS },
                                                onOtherClick = { nestedScreen = NestedScreen.OTHER_SETTINGS },
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
                                    friendsBadgeCount = friendsViewModel.pendingRequests.size,
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
                                    onHeightMeasured = { navDockHeight = it },
                                )
                                }
                            }
                        }
                    }

                    // Always composed (not gated inside the `when` above) so its exit transition
                    // has something to animate — a screen selected by `when` gets torn out of
                    // composition the instant its condition flips, before any exit animation
                    // could actually play. This is the one nested screen with its own distinct
                    // entrance: slides up from the bottom like a sheet, every time, rather than
                    // appearing instantly the way every other nested screen still does.
                    AnimatedVisibility(
                        visible = nestedScreen == NestedScreen.GOLD,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                    ) {
                        EmberGoldScreen(onBack = { nestedScreen = null })
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
