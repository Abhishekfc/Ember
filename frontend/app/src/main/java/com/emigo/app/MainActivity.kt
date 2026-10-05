package com.emigo.app

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

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
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.emigo.app.data.SignInOutcome
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
import com.emigo.app.ui.settings.AppIconSwitcher
import com.emigo.app.ui.settings.BlockedUsersScreen
import com.emigo.app.ui.settings.BlockedUsersViewModel
import com.emigo.app.ui.settings.EmberGoldScreen
import com.emigo.app.ui.settings.EmberGoldViewModel
import com.emigo.app.ui.settings.OtherSettingsScreen
import com.emigo.app.ui.settings.SettingsScreen
import com.emigo.app.ui.settings.WidgetSettingsScreen
import com.emigo.app.ui.settings.WidgetSettingsViewModel
import com.emigo.app.ui.theme.EmberAppTheme
import com.emigo.app.ui.theme.EmberBackground
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.ThemeKey
import com.emigo.app.ui.theme.ThemeScreen
import com.emigo.app.ui.theme.ThemeViewModel
import com.emigo.app.ui.theme.emberThemeDefinition
import com.emigo.app.widget.EmberWidget
import com.emigo.app.widget.WidgetPhotoStore
import com.emigo.app.widget.WidgetPhotoSync
import com.emigo.app.widget.WidgetPreferenceStore
import com.emigo.app.widget.WidgetUpdateWorker
import androidx.glance.appwidget.updateAll
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** Screens reached from within a tab (Settings -> Theme, Friends -> Find People / Friend Profile)
 * rather than from the bottom nav. Kept apart from the current page so back pops just the nested
 * screen without losing which page you were on. Camera isn't one of these: it's a swipeable page
 * of the main pager. Activity is, since it moved from the nav dock to the bell in Home's header. */
private enum class NestedScreen { THEME, FIND_PEOPLE, FRIEND_PROFILE, PROFILE, GOLD, WIDGET_SETTINGS, BLOCKED_USERS, OTHER_SETTINGS, SENT_PHOTOS, ACTIVITY }

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

/** The nav-dock tab that reads as active for a page. Camera has no tab (its icon fades out near
 * that page, see the dock's alpha graphicsLayer below), so it falls back to Home. */
private fun destinationForPage(page: Int): NavDestination = when (page) {
    PAGE_MEMORIES -> NavDestination.MEMORIES
    PAGE_HOME -> NavDestination.HOME
    PAGE_FRIENDS -> NavDestination.FRIENDS
    PAGE_SETTINGS -> NavDestination.SETTINGS
    else -> NavDestination.HOME
}

class MainActivity : ComponentActivity() {

    // Hoisted to EmberApplication so they're process-wide singletons that survive this Activity
    // being recreated by a config change, instead of each recreation wiring ViewModels to an
    // orphaned NetworkModule.
    private val emberApplication get() = application as EmberApplication
    private val networkModule get() = emberApplication.networkModule
    private val authRepository get() = emberApplication.authRepository
    private val photoRepository get() = emberApplication.photoRepository
    private val friendRepository get() = emberApplication.friendRepository
    private val activityRepository get() = emberApplication.activityRepository
    private val stringProvider get() = emberApplication.stringProvider
    private val userRepository get() = emberApplication.userRepository
    private val subscriptionRepository get() = emberApplication.subscriptionRepository
    private val billingManager get() = emberApplication.billingManager
    private val safetyRepository get() = emberApplication.safetyRepository
    private val themePreferenceStore get() = emberApplication.themePreferenceStore
    private val appIconPreferenceStore get() = emberApplication.appIconPreferenceStore
    private val notificationPreferenceStore get() = emberApplication.notificationPreferenceStore
    private val localListCache get() = emberApplication.localListCache

    // Compose state so the LaunchedEffect below re-runs on change. Set from the latest launch
    // intent (onCreate on a cold start, onNewIntent when singleTask reuses the running Activity)
    // and nulled once acted on, so a notification tap isn't processed twice across a recomposition.
    private var pendingNotificationIntent by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationIntent = intent
        // True edge-to-edge: transparent system bars, with each screen's full-bleed background
        // painting behind them. A flat fill color seamed visibly on themes whose background isn't
        // flat across the top edge (e.g. Ember's off-center radial gradient). Content that would
        // sit under the bars needs its own statusBarsPadding()/navigationBarsPadding().
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Otherwise the system paints a translucent dark scrim behind the gesture bar, the flat
            // mismatched strip this removes. Screens already have enough contrast of their own.
            window.isNavigationBarContrastEnforced = false
        }
        // Keep the widget's background refresh alive even across reinstalls that wiped the
        // schedule; KEEP policy makes this a no-op when it's already queued.
        WidgetUpdateWorker.schedule(applicationContext)
        // Whether to draw Home or Login on the first frame, read before the first composition.
        // Doing it in a LaunchedEffect meant every cold start rendered one empty placeholder frame
        // first (invisible with flat gradient backgrounds, obvious with an image-backed theme).
        // Firebase persists the session itself, so no local read is needed.
        var hasSavedSession = FirebaseAuth.getInstance().currentUser != null
        // TokenStore's local echo of the last NeedsVerification outcome, read synchronously so a
        // still-pending account's cold start shows the verification screen on frame one instead of
        // the app shell. Matched against the current uid, so a stale entry from a previous account
        // on this device never applies (TokenStore.clear removes it on sign-out).
        var pendingVerification = runBlocking { networkModule.tokenStore.readPendingVerification() }
            ?.takeIf { it.firebaseUid == FirebaseAuth.getInstance().currentUser?.uid }
        // A deadline already in the past means EmailVerificationExpiryService will delete the
        // account on its next sweep (or already has), so there's nothing to resume. Priming the
        // verification screen from it would land straight on "Verification failed" with no way
        // forward. Signing out locally before the first frame means a restart after the countdown
        // ran out lands on a clean Welcome screen.
        if (pendingVerification != null && pendingVerification.deadlineMillis <= System.currentTimeMillis()) {
            FirebaseAuth.getInstance().signOut()
            runBlocking { networkModule.tokenStore.clear() }
            hasSavedSession = false
            pendingVerification = null
        }
        // Read once, synchronously, before the first frame so a returning user's first composition
        // already shows real content (feed, cached Memories thumbnails, profile picture). Reading
        // asynchronously caused an "everything reloads" flash on every restart even though the data
        // was cached. A few tiny local reads block for milliseconds, before anything is on screen.
        // var, not val: reset on sign-out (see onSignOut), otherwise signing into a different
        // account in the same process seeded the new HomeViewModel with the previous account's
        // snapshot until a later fetch overwrote it.
        var initialHomeCache = runBlocking {
            InitialHomeCache(
                feedItems = localListCache.read<FeedItem>(LocalListCache.KEY_FEED) ?: emptyList(),
                memories = localListCache.read<MemoryPhotoDto>(LocalListCache.KEY_MEMORIES) ?: emptyList(),
                profile = localListCache.readObject<UserProfileDto>(LocalListCache.KEY_PROFILE),
            )
        }
        // Preloads only the photo Home's featured card shows first (page 0: the first feed item's
        // newest photo, see buildHomeCarousel/pageIndexFor), asking Coil before Compose starts for a
        // head start on the session's first image request. Sized to the screen width, not full
        // resolution: an unsized preload of this app's multi-MB test images was part of the problem
        // (see FirstPhotoPreloader).
        FirstPhotoPreloader.preload(
            applicationContext,
            initialHomeCache.feedItems.firstOrNull()?.photos?.lastOrNull()?.photoUrl,
            targetWidthPx = resources.displayMetrics.widthPixels,
        )
        // Same head start for the current theme's background image, if it has one (several themes
        // are plain gradients). Reads whatever is persisted, the same synchronous last-known value
        // ThemeViewModel seeds from; never hardcoded to a theme.
        val lastTheme = themePreferenceStore.lastEffectiveThemeSync()
        val lastThemeBackground = emberThemeDefinition(lastTheme).colors.background
        if (lastThemeBackground is EmberBackground.ImageBacked) {
            FirstPhotoPreloader.preloadDrawable(
                applicationContext,
                lastThemeBackground.drawableResId,
                targetWidthPx = resources.displayMetrics.widthPixels,
                targetHeightPx = resources.displayMetrics.heightPixels,
            )
        }
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ThemeViewModel(themePreferenceStore, subscriptionRepository) }
                },
            )
            // Non-null only while ThemeScreen browses with an unapplied pick staged, so the whole app
            // can live-preview a theme without touching the persisted selection until Apply.
            // ThemeScreen resets it on Apply and whenever it leaves composition, so a preview never
            // outlives the screen.
            var previewThemeKey by remember { mutableStateOf<ThemeKey?>(null) }

            // Hoisted above EmberAppTheme so picking a theme re-themes the whole app immediately,
            // not just that screen.
            EmberAppTheme(themeKey = previewThemeKey ?: themeViewModel.selectedTheme) {
                // Bumped by onSignOut to force a fresh LoginViewModel. Sign-out calls
                // viewModelStore.clear(), which cancels every ViewModel's scope, including the login
                // one's, even though sign-out lands on the login screen. A cancelled scope doesn't
                // throw: viewModelScope.launch bodies just never run, so the screen looked normal
                // while its buttons did nothing (reachable in one tap from "Try again later" on the
                // expired-verification screen, and only a restart fixed it). Keying on this counter
                // retrieves a new instance with a live scope.
                var loginSessionId by remember { mutableIntStateOf(0) }
                val loginViewModel: LoginViewModel = viewModel(
                    key = "login-$loginSessionId",
                    factory = viewModelFactory {
                        initializer {
                            LoginViewModel(
                                stringProvider,
                                authRepository,
                                initialPendingVerificationEmail = pendingVerification?.email,
                                initialPendingVerificationDeadlineMillis = pendingVerification?.deadlineMillis,
                            )
                        }
                    },
                )
                // Seeded from the synchronous reads in onCreate, so there's no "unknown yet"
                // placeholder: a verified returning user gets Home on frame one, a signed-out user
                // Login, and a signed-in user who still needs to verify gets that screen (see
                // pendingVerification). resumeSession's LaunchedEffect re-confirms all of this
                // against the network; this is only the best guess before then.
                var authenticated by remember { mutableStateOf(hasSavedSession && pendingVerification == null) }
                var nestedScreen by remember { mutableStateOf<NestedScreen?>(null) }
                var selectedProfileSubject by remember { mutableStateOf<ProfileSubject?>(null) }
                // Where closing the friend profile lands. nestedScreen holds one screen, not a back
                // stack, so a profile opened from another nested screen (Activity, Find People) must
                // return there instead of falling through to the pager (which lost your Find People
                // search results). Null means opened from a pager tab. Every site that opens a
                // profile sets this explicitly, so a value never lingers from an earlier visit.
                var friendProfileReturnTo by remember { mutableStateOf<NestedScreen?>(null) }
                val appContext = LocalContext.current

                // One shared instance: read for the Settings badge, written once per session by the
                // Gold-status LaunchedEffect, and reused by onSignOut's cleanup.
                val widgetPreferenceStore = remember { WidgetPreferenceStore(applicationContext) }
                // Mirrors the Gold status written into widgetPreferenceStore's cache (see that
                // LaunchedEffect), as Compose state so Settings can show a Gold/Free badge without
                // re-reading DataStore.
                var isGoldMember by remember { mutableStateOf(false) }
                val widgetFeaturedFriendIds by widgetPreferenceStore.featuredFriendIds.collectAsState(initial = emptySet())

                // The bars are transparent (set in onCreate); per recomposition only the icon tint
                // changes: light icons on a dark background, dark on light, using the active theme
                // once signed in or AuthPalette's fixed look before login.
                val barsAreLight = if (authenticated) EmberTheme.colors.isLight else AuthPalette.colors.isLight
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = barsAreLight
                    insetsController.isAppearanceLightNavigationBars = barsAreLight
                }

                // Camera is a swipeable page, so the slow ProcessCameraProvider fetch (once absorbed
                // by the camera button tap) is likely to show as a black flash mid-swipe. Resolving
                // it this early usually has it warm (CameraX keeps it as a singleton) by the time
                // Camera is reached. It doesn't remove the separate bindToLifecycle cost in
                // CameraScreen, so the flash is shortened, not eliminated.
                LaunchedEffect(Unit) { ProcessCameraProvider.getInstance(appContext) }
                val coroutineScope = rememberCoroutineScope()

                // Used by Settings' Sign out and by the handler below for an expired or invalid token
                // (401); both must land on a clean login screen.
                val onSignOut = {
                    // Detach this device from the account's push list before the token that
                    // authenticates the call is cleared (see AuthRepository.unregisterDeviceToken):
                    // leaving it attached kept delivering the old account's photo notifications,
                    // friend names included, to a signed-out phone. Sequential in one coroutine for
                    // that ordering; the other cleanup below is independent and parallel.
                    coroutineScope.launch {
                        val fcmToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                        if (fcmToken != null) authRepository.unregisterDeviceToken(fcmToken)
                        networkModule.tokenStore.clear()
                        // Ends the session. Must follow the unregister call, which needs a valid
                        // Firebase identity.
                        FirebaseAuth.getInstance().signOut()
                    }
                    // LocalListCache isn't per-account; without this a different user signing in would
                    // briefly see this account's cached feed, friends, activity and memories.
                    coroutineScope.launch { localListCache.clearAll() }
                    // Reset with LocalListCache (see where it's declared): otherwise the next
                    // HomeViewModel seeds from this account's in-memory snapshot, which the on-disk
                    // clearAll() doesn't touch.
                    initialHomeCache = InitialHomeCache()
                    // These process-wide singletons (see EmberApplication) outlive any one account,
                    // and their TTL caches are keyed without account identity. Without clearing,
                    // signing into another account within ~30s could serve the previous account's
                    // feed, friends or activity from cache on what looks like a normal fetch.
                    photoRepository.clearCache()
                    friendRepository.clearCache()
                    activityRepository.clearCache()
                    subscriptionRepository.clearCache()
                    // Persisted to disk (SubscriptionRepository.lastKnownIsActive), so it needs its own
                    // clear: otherwise a different account signing in offline would inherit the
                    // previous account's last-confirmed Gold status.
                    coroutineScope.launch { subscriptionRepository.clearLastKnownStatus() }
                    // Theme is a local, device-scoped preference with no backend copy (see
                    // ThemePreferenceStore.clear); without this, another account would inherit the
                    // previous theme, Gold-gated ones included.
                    coroutineScope.launch { themePreferenceStore.clear() }
                    // The disk clear doesn't touch this ViewModel's in-memory state (see
                    // ThemeViewModel.reset), which is why a Gold-gated theme kept applying after
                    // sign-out.
                    themeViewModel.reset()
                    // Same as the theme, plus: the launcher icon is a real OS-level setting (see
                    // AppIconSwitcher), so it would keep showing a Gold subscriber's choice after
                    // sign-out.
                    coroutineScope.launch {
                        appIconPreferenceStore.clear()
                        AppIconSwitcher.apply(applicationContext, AppIconKey.DEFAULT)
                    }
                    coroutineScope.launch { notificationPreferenceStore.clear() }
                    // The widget reads its cached photo (and for Gold, its featured-friend choice and
                    // cached Gold status) regardless of sign-in state; without this a friend's private
                    // photo and name, or the old account's customization, keeps applying after
                    // sign-out.
                    coroutineScope.launch {
                        WidgetPhotoStore(applicationContext).clear()
                        widgetPreferenceStore.clear()
                        EmberWidget().updateAll(applicationContext)
                    }
                    // Coil keeps every photo this account viewed (friends' photos, profile pictures)
                    // in an on-disk cache that survived sign-out. Clearing the widget's one cached
                    // photo while leaving that history was inconsistent. Costs only a re-download of
                    // whatever is viewed again.
                    SingletonImageLoader.get(applicationContext).let { loader ->
                        loader.memoryCache?.clear()
                        coroutineScope.launch(Dispatchers.IO) { loader.diskCache?.clear() }
                    }
                    // Per-account ViewModels (feed, friends, login form...) live in the Activity's
                    // ViewModelStore and are retrieved by class/key however often `authenticated`
                    // flips; without clearing, a new account would see the previous one's cached
                    // data and stale form fields.
                    viewModelStore.clear()
                    // Must follow clear(), so the login screen gets a LoginViewModel with a live
                    // scope (see loginSessionId).
                    loginSessionId++
                    authenticated = false
                    nestedScreen = null
                    selectedProfileSubject = null
                    // pendingVerification is onCreate's one-time cold-start snapshot, still captured by
                    // the loginViewModel factory. Left set, every LoginViewModel built after this
                    // sign-out was re-primed onto NEEDS_EMAIL_VERIFICATION with the same expired
                    // deadline, which made "Try again later" look broken: it signed out, then the new
                    // screen immediately showed expired again. Nulling it gives any account reached
                    // from here (new sign-up, different sign-in, back to Welcome) a clean start.
                    pendingVerification = null
                }

                // A 401 means the session expired or is invalid; return to login instead of sitting on
                // a permanent "couldn't load" error.
                LaunchedEffect(Unit) {
                    networkModule.sessionExpired.collect { onSignOut() }
                }

                // The third place email verification is enforced, after sign-up (submitUsername) and
                // sign-in (AuthRepository.checkExistingProfile): a session resumed from Firebase's
                // cached state renders the app shell on frame one without either of those running.
                // Without this, reopening the app between the verification deadline passing and
                // EmailVerificationExpiryService deleting the row put an unverified account back in.
                //
                // One authoritative check per launch (resumeSession reloads and force-refreshes
                // before answering), replacing a passive 403 listener that acted on whatever cached
                // token a background caller (especially the widget sync worker) sent, and kept
                // throwing already-verified people back to verification and restarting the
                // countdown. A failure is ignored: it usually means offline, which must never sign
                // anyone out.
                LaunchedEffect(Unit) {
                    if (!hasSavedSession) return@LaunchedEffect
                    authRepository.resumeSession().onSuccess { outcome ->
                        when (outcome) {
                            is SignInOutcome.NeedsVerification -> {
                                loginViewModel.showVerificationRequired(outcome)
                                authenticated = false
                            }
                            is SignInOutcome.SignedIn -> {
                                // Frame one guessed "still pending" from TokenStore's local echo (see
                                // pendingVerification in onCreate) and this check proved it wrong:
                                // verification finished somewhere this device didn't see (another
                                // device, or the link opened outside the app). Without this, a
                                // verified account would be stuck on the verification screen.
                                if (!authenticated) authenticated = true
                            }
                            is SignInOutcome.NeedsProfile -> {
                                // Acted on only mid-verification-flow: hasSavedSession is true and
                                // authenticated is false here only when onCreate believed the account
                                // still needed to verify. NeedsProfile then means
                                // EmailVerificationExpiryService has since deleted the row (onCreate
                                // catches only a deadline already past before launch; this catches
                                // one that tipped over, or was swept, in the seconds since). Nothing
                                // is left to resume, so sign out to Welcome instead of leaving the
                                // verification dead end on screen.
                                if (!authenticated) onSignOut()
                            }
                        }
                    }
                }

                // Re-fires on every authenticated transition: a fresh login, or an already-valid
                // session at cold start. A token fetched before this (e.g. onNewToken landing while
                // signed out) is skipped there, so fetching the current token here covers both
                // orderings with one path.
                LaunchedEffect(authenticated) {
                    if (authenticated) {
                        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                        if (token != null) authRepository.registerDeviceToken(token)
                    }
                }

                // Refreshes the widget's cached Gold status once per authenticated session, the same
                // one-shot-on-open pattern CameraViewModel and ThemeViewModel use for their Gold
                // checks. WidgetPhotoSync reads this cache instead of checking live on every sync
                // (including the 6h worker and pushes), so a lapsed subscription self-heals on the
                // next app open. Shares SubscriptionRepository's TTL cache with those checks, so it
                // rarely adds a network round trip.
                LaunchedEffect(authenticated) {
                    if (authenticated) {
                        // isGoldMemberOrLastKnown(), not a bare getStatus(): opening the app offline
                        // must never overwrite this cache with false for a real subscriber (see
                        // SubscriptionRepository).
                        isGoldMember = subscriptionRepository.isGoldMemberOrLastKnown()

                        // The backend re-checks Google only when verifyPurchase is called; it doesn't
                        // notice a renewal on its own, so its stored expiresAt can lapse though Play
                        // renewed. This is the same reconciliation EmberGoldViewModel.refresh() does
                        // for a reinstall or new device, so a subscriber who never reopens the Gold
                        // screen doesn't see Gold vanish app-wide at their first renewal.
                        if (!isGoldMember) {
                            billingManager.findActivePurchase()?.let { existing ->
                                subscriptionRepository.verifyPurchase(existing.productId, existing.purchaseToken)
                                    .onSuccess { isGoldMember = it.isActive }
                            }
                        }
                        widgetPreferenceStore.setCachedIsGoldMember(isGoldMember)
                    }
                }

                if (!authenticated) {
                    LoginScreen(
                        viewModel = loginViewModel,
                        onAuthenticated = {
                            authenticated = true
                            // Re-resolves this account's saved theme and real Gold status (see
                            // ThemeViewModel.reload).
                            themeViewModel.reload()
                        },
                        onSignOut = onSignOut,
                    )
                } else {
                    var showRecipientPicker by remember { mutableStateOf(false) }

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

                    // Tapping Home's featured photo throws everything else out of focus, the shared nav
                    // dock included. Hoisted here because the dock is one instance shared by every
                    // page, not rendered per screen.
                    var isHomePhotoFocused by remember { mutableStateOf(false) }

                    // Hoisted from HomeScreen so the nav dock, which lives outside it, can read Home's
                    // live scroll position for the icon morph (as it used to read the outer pager's
                    // swipe position).
                    val homeScrollState = rememberScrollState()
                    // Hoisted for another reason: FriendsScreen is fully disposed while a friend's
                    // profile is open (the nestedScreen `when` composes one branch at a time), so a
                    // scroll position it owned would reset to the top on every return.
                    val friendsListState = rememberLazyListState()
                    // Same reason as friendsListState, for a more visible bug: remembered inside the
                    // `else` branch with the HorizontalPager, every return from any nested screen
                    // reset it to FALLBACK_NAV_DOCK_HEIGHT_DP for one frame before BottomNavDock's
                    // onSizeChanged corrected it. Every screen reserves bottom space equal to this
                    // (see LocalNavDockHeight), so the guess-then-correct made the whole page visibly
                    // shift ("vibrate"). Hoisting sets it once and never guesses again this session.
                    var navDockHeight by remember { mutableStateOf(FALLBACK_NAV_DOCK_HEIGHT_DP) }

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
                                    stringProvider,
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
                                    stringProvider,
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
                    // A NEW_PHOTO push updates the widget directly (WidgetPhotoSync.syncFromPush in
                    // EmberFirebaseMessagingService, no live app state needed); this is the other
                    // half, letting a live HomeViewModel pick up the change too. loadFeed() only
                    // updates syncedFeedItems (see HomeViewModel), never the visible session, so a
                    // push landing mid-swipe can't interrupt; it only shows the "New memories
                    // available" indicator.
                    LaunchedEffect(Unit) {
                        emberApplication.newPhotoPushEvents.collect { homeViewModel.loadFeed() }
                    }
                    // FriendsViewModel otherwise refetches only at app start or on pull-to-refresh,
                    // so "Last sent" and streak on the Friends tab went stale when a friend's photo
                    // arrived. A received photo changes exactly those fields, so it gets the same
                    // treatment as Home's feed above.
                    LaunchedEffect(Unit) {
                        emberApplication.newPhotoPushEvents.collect { friendsViewModel.refreshSilently() }
                    }
                    // The other direction: a finished queued send (see PendingSendWorker) is my own
                    // new photo, so both Feed and Memories need refreshing, unlike the push case
                    // above, which needs only Feed.
                    LaunchedEffect(Unit) {
                        emberApplication.photoSendCompletedEvents.collect {
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
                            initializer { ActivityViewModel(stringProvider, activityRepository, localListCache) }
                        },
                    )
                    // Camera is a pager page, not a screen created on entry; hoisted with the other tab
                    // ViewModels so its state (notably capturedFile, which gates swiping, see
                    // userScrollEnabled below) exists whichever page is current.
                    val cameraViewModel: CameraViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                CameraViewModel(
                                    stringProvider,
                                    friendRepository,
                                    photoRepository,
                                    subscriptionRepository,
                                    localListCache,
                                    emberApplication.cameraHintPreferenceStore,
                                )
                            }
                        },
                    )

                    // Camera's recipient list (see hasLoadedCameraFriends) is fetched once per session
                    // and never on its own afterward; without this, a friend request accepted
                    // anywhere stayed invisible in Camera's picker until restart, since nothing told
                    // this long-lived ViewModel its copy was stale.
                    LaunchedEffect(Unit) {
                        emberApplication.friendsChangedEvents.collect { cameraViewModel.loadFriends() }
                    }
                    LaunchedEffect(Unit) {
                        emberApplication.friendsChangedEvents.collect { friendsViewModel.refreshSilently() }
                    }
                    // Flips the outbox button's animation from SENDING to its checkmark (see
                    // CameraViewModel.markSendComplete for why this coarse, not photo-specific signal
                    // is good enough). A separate collector because cameraViewModel isn't declared
                    // yet at the earlier one.
                    LaunchedEffect(Unit) {
                        emberApplication.photoSendCompletedEvents.collect { cameraViewModel.markSendComplete() }
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
                        if (pagerState.settledPage != PAGE_HOME) isHomePhotoFocused = false
                    }
                    // Clears the header bell's badge once Activity is actually the shown nested
                    // screen, not on the bell tap alone ("only counts once visible", as when
                    // Activity was a pager page keyed on settledPage).
                    LaunchedEffect(nestedScreen) {
                        if (nestedScreen == NestedScreen.ACTIVITY) activityViewModel.markSeen()
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
                        nestedScreen = friendProfileReturnTo
                        friendProfileReturnTo = null
                        selectedProfileSubject = null
                    }

                    // Back retraces the last navigation step instead of falling through to the system
                    // (which closes the app): close the recipient picker, then any nested screen,
                    // then return to Home before exiting. Camera has its own higher-priority
                    // BackHandler (in CameraScreen) for "back retakes" while a capture is pending;
                    // this one fires only once that no longer applies.
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

                    // A nav dock tap (a tab or the camera button) is a direct jump, not a swipe, so it
                    // shouldn't animate through every page in between. scrollToPage snaps straight
                    // there; only an actual drag animates through the pages it crosses.
                    val onNavigate: (NavDestination) -> Unit = { destination ->
                        nestedScreen = null
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
                    // ignored. Nulls the pending intent once handled (or found irrelevant) so
                    // recomposition can't replay it.
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

                    // Wraps the nested-screen `when` and the Gold overlay so Gold renders on top of
                    // whatever the `when` shows, instead of being another mutually exclusive branch.
                    Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        nestedScreen == NestedScreen.PROFILE -> {
                            val myProfileViewModel: MyProfileViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        MyProfileViewModel(
                                            stringProvider,
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

                        // NestedScreen.GOLD isn't a branch here; see the Box/AnimatedVisibility around
                        // this whole `when`, below.

                        // Activity is reached from the bell in Home's header (see HomeScreen's
                        // onActivityClick), not a dock tab or swipe. onCameraClick and
                        // onNavigateToFriends must close this nested screen and move the pager (the
                        // pattern FriendProfileScreen's onSendPhotoClick uses): closing alone would
                        // leave the pager on its current page underneath.
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
                            // Both go through the same resolver, so "is this tappable" and "what does
                            // it open" can't disagree.
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
                                        WidgetSettingsViewModel(stringProvider, friendRepository, subscriptionRepository, widgetPreferenceStore)
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
                                    initializer { BlockedUsersViewModel(stringProvider, safetyRepository) }
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
                                    initializer { SentPhotosViewModel(stringProvider, photoRepository) }
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
                                // Same local cleanup and return to login as a manual sign-out; no
                                // account is left for the cached state to belong to.
                                onAccountDeleted = onSignOut,
                            )
                        }

                        nestedScreen == NestedScreen.FIND_PEOPLE -> {
                            val findPeopleViewModel: FindPeopleViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { FindPeopleViewModel(stringProvider, friendRepository) }
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
                            // A ViewModel cached under a hand-built string key (userId alone, then subject
                            // kind + userId) eventually collides, because the same person is revisited
                            // many times as the relationship changes (stranger, requested, friend,
                            // removed, stranger). Each is a new visit, but viewModel(key = X) runs its
                            // factory only on the first lookup for a key and returns that stale instance
                            // afterward. The right scope is "one visit to this screen", so it gets its
                            // own ViewModelStore, created whenever `subject` changes and cleared by the
                            // DisposableEffect below (cancelling its viewModelScope too), like a
                            // back-stack entry. Jetpack Navigation does the same per entry.
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
                                    initializer { FriendProfileViewModel(stringProvider, friendRepository, safetyRepository, subject) }
                                },
                            )
                            FriendProfileScreen(
                                viewModel = friendProfileViewModel,
                                onBack = onCloseFriendProfile,
                                onSendPhotoClick = {
                                    // Sending from a friend's profile means that friend, and only that
                                    // friend, ends up selected in Camera, not whatever was selected
                                    // before (typically the pinned partner via CameraViewModel's
                                    // default). Safe unconditionally: Send a photo shows only for
                                    // ProfileSubject.Friend, which always has a friendId.
                                    cameraViewModel.setSelectedRecipients(setOf(subject.userId))
                                    // onCameraClick only scrolls the pager; without closing this
                                    // screen the pager scrolled underneath while FriendProfileScreen
                                    // kept covering it, hiding Camera until back was pressed.
                                    onCloseFriendProfile()
                                    onCameraClick()
                                },
                                // subject.friendshipId is the id this screen was opened with, stable
                                // while it's open, so it's the key each of these needs to update the
                                // Friends tab's list in place with no fetch.
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
                                    // Same local-list update as onRemoved: blocking also deletes any
                                    // friendship server-side (see BlockService), and a pending
                                    // request between the two is invalid once blocked.
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
                                        RecipientPickerViewModel(
                                            stringProvider,
                                            friendRepository,
                                            localListCache,
                                            cameraViewModel.selectedRecipientIds,
                                            cameraViewModel.friends,
                                        )
                                    }
                                },
                            )
                            // On reopen this is an existing ViewModel instance (viewModel() reuses it
                            // while the store owner lives), not freshly fetched. Unlike cameraViewModel
                            // and friendsViewModel, its collector below runs only while this branch is
                            // composed, so a friend accepted elsewhere while the picker was closed
                            // emitted a friendsChangedEvents signal nobody was listening for. That
                            // SharedFlow has no replay, so a late collector never sees it, and the
                            // picker kept showing stale data until the app restarted.
                            //
                            // The loadFriends() below closes that gap by refreshing on every open. The
                            // collector remains for what an on-open refresh can't cover: a friend
                            // accepted from another screen while the picker is already open.
                            LaunchedEffect(Unit) {
                                recipientPickerViewModel.loadFriends()
                                // Once per open, not per recomposition; see
                                // RecipientPickerViewModel.sortSnapshot for why the sort order freezes
                                // here instead of tracking the live selection.
                                recipientPickerViewModel.refreshSortSnapshot()
                                emberApplication.friendsChangedEvents.collect { recipientPickerViewModel.loadFriends() }
                            }
                            RecipientPickerScreen(
                                viewModel = recipientPickerViewModel,
                                onClose = { showRecipientPicker = false },
                                onConfirm = { ids ->
                                    cameraViewModel.setSelectedRecipients(ids)
                                    showRecipientPicker = false
                                },
                                onAddFriend = {
                                    showRecipientPicker = false
                                    nestedScreen = NestedScreen.FIND_PEOPLE
                                },
                            )
                        }

                        else -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // navDockHeight is hoisted above this `when` (see its comment there).
                                // Screens read it via LocalNavDockHeight instead of a fixed dp, which
                                // once left Settings' Log out button partly under the dock on a real
                                // device. defaultOverscrollFactory is captured here, before the
                                // pager's scope nulls the local, and re-provided inside page content so
                                // each tab's vertical lists keep normal overscroll; only the pager's
                                // horizontal edge-of-tabs bounce is disabled.
                                val defaultOverscrollFactory = LocalOverscrollFactory.current
                                CompositionLocalProvider(
                                    LocalNavDockHeight provides navDockHeight,
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
                                    // it after it's drawn.
                                    beyondViewportPageCount = 1,
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
                                            val notificationsEnabled by notificationPreferenceStore.enabled
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
                                                    coroutineScope.launch { notificationPreferenceStore.save(enabled) }
                                                },
                                                onCameraClick = onCameraClick,
                                                onProfileClick = { nestedScreen = NestedScreen.PROFILE },
                                                onThemeClick = { nestedScreen = NestedScreen.THEME },
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
                                    onHeightMeasured = { navDockHeight = it },
                                )
                                }
                            }
                        }
                    }

                    // Always composed (not gated inside the `when`) so its exit transition has
                    // something to animate; a screen selected by `when` leaves composition the
                    // instant its condition flips, before an exit animation could play. The one
                    // nested screen with its own entrance: it slides up like a sheet every time
                    // instead of appearing instantly.
                    AnimatedVisibility(
                        visible = nestedScreen == NestedScreen.GOLD,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                    ) {
                        val goldViewModel: EmberGoldViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer { EmberGoldViewModel(stringProvider, billingManager, subscriptionRepository) }
                            },
                        )
                        EmberGoldScreen(
                            viewModel = goldViewModel,
                            onBack = { nestedScreen = null },
                            onGoldActivated = {
                                // The purchase already updated SubscriptionRepository's caches; this pulls
                                // the "you're Gold now" answer into the app-wide state other screens
                                // read before they'd re-check.
                                isGoldMember = true
                                coroutineScope.launch { widgetPreferenceStore.setCachedIsGoldMember(true) }
                                themeViewModel.reload()
                            },
                        )
                    }
                    }
                }
            }
        }
        // The manifest's importantForAutofill="noExcludeDescendants" on this Activity doesn't reach
        // Compose content: Compose's ComposeView marks itself important for autofill regardless of
        // its ancestors. setContent() attaches that ComposeView synchronously as the content root's
        // only child, so right after it returns the flag can be forced on the view directly. This
        // (not the manifest attribute, KeyboardType, or disableAutofillServices(), which no-ops
        // unless its one-time system dialog is accepted) is what stops Google Password Manager's
        // "Save password?" prompt on every login.
        (findViewById<ViewGroup>(android.R.id.content))?.getChildAt(0)
            ?.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
}
