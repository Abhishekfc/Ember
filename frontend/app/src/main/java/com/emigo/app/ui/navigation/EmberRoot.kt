package com.emigo.app.ui.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.SingletonImageLoader
import com.emigo.app.EmberApplication
import com.emigo.app.data.repository.SignInOutcome
import com.emigo.app.data.local.TokenStore
import com.emigo.app.ui.auth.AuthPalette
import com.emigo.app.ui.auth.LoginScreen
import com.emigo.app.ui.auth.LoginViewModel
import com.emigo.app.ui.home.InitialHomeCache
import com.emigo.app.ui.settings.AppIconKey
import com.emigo.app.ui.settings.AppIconSwitcher
import com.emigo.app.ui.theme.EmberAppTheme
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.ThemeKey
import com.emigo.app.ui.theme.ThemeViewModel
import com.emigo.app.widget.WidgetPreferenceStore
import com.emigo.app.widget.WidgetSession
import com.emigo.app.widget.WidgetUpdateWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** What MainActivity.onCreate reads synchronously, before the first frame, handed to the Compose
 * tree. Sign-out resets [pendingVerification] and [initialHomeCache] (see onSignOut in
 * [EmberRoot]); the ViewModel factories read them at the moment they run, so the next account
 * never starts from the previous one's values. */
internal class ColdStartSnapshot(
    val hasSavedSession: Boolean,
    var pendingVerification: TokenStore.PendingVerification?,
    var initialHomeCache: InitialHomeCache,
)

/** The top of the Compose tree: the theme, the sign-in gate, the session checks and sign-out,
 * then either the login flow or [SignedInShell]. */
@Composable
internal fun EmberRoot(
    activity: ComponentActivity,
    app: EmberApplication,
    coldStart: ColdStartSnapshot,
    pendingNotificationIntent: () -> Intent?,
    onNotificationIntentHandled: () -> Unit,
) {
    val themeViewModel: ThemeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ThemeViewModel(app.themePreferenceStore, app.subscriptionRepository) }
        },
    )
    // Non-null while ThemeScreen browses with an unapplied pick staged, so the whole app
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
                        app.stringProvider,
                        app.authRepository,
                        initialPendingVerificationEmail = coldStart.pendingVerification?.email,
                        initialPendingVerificationDeadlineMillis = coldStart.pendingVerification?.deadlineMillis,
                    )
                }
            },
        )
        // Seeded from the synchronous reads in onCreate, so there's no "unknown yet"
        // placeholder: a verified returning user gets Home on frame one, a signed-out user
        // Login, and a signed-in user who still needs to verify gets that screen (see
        // pendingVerification). resumeSession's LaunchedEffect re-confirms all of this
        // against the network; this is only the best guess before then.
        var authenticated by remember { mutableStateOf(coldStart.hasSavedSession && coldStart.pendingVerification == null) }
        val nav = remember { AppNavState() }
        val appContext = LocalContext.current

        // One shared instance: read for the Settings badge, written once per session by the
        // Gold-status LaunchedEffect, and reused by onSignOut's cleanup.
        val widgetPreferenceStore = remember { WidgetPreferenceStore(app) }
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
            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
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
                if (fcmToken != null) app.authRepository.unregisterDeviceToken(fcmToken)
                app.networkModule.tokenStore.clear()
                // Ends the session. Must follow the unregister call, which needs a valid
                // Firebase identity.
                FirebaseAuth.getInstance().signOut()
            }
            // LocalListCache isn't per-account; without this a different user signing in would
            // briefly see this account's cached feed, friends, activity and memories.
            coroutineScope.launch { app.localListCache.clearAll() }
            // Reset with LocalListCache (see ColdStartSnapshot): otherwise the next
            // HomeViewModel seeds from this account's in-memory snapshot, which the on-disk
            // clearAll() doesn't touch.
            coldStart.initialHomeCache = InitialHomeCache()
            // These process-wide singletons (see EmberApplication) outlive any one account,
            // and their TTL caches are keyed without account identity. Without clearing,
            // signing into another account within ~30s could serve the previous account's
            // feed, friends or activity from cache on what looks like a normal fetch.
            app.photoRepository.clearCache()
            app.friendRepository.clearCache()
            app.activityRepository.clearCache()
            app.subscriptionRepository.clearCache()
            // Persisted to disk (SubscriptionRepository.lastKnownIsActive), so it needs its own
            // clear: otherwise a different account signing in offline would inherit the
            // previous account's last-confirmed Gold status.
            coroutineScope.launch { app.subscriptionRepository.clearLastKnownStatus() }
            // Theme is a local, device-scoped preference with no backend copy (see
            // ThemePreferenceStore.clear); without this, another account would inherit the
            // previous theme, Gold-gated ones included.
            coroutineScope.launch { app.themePreferenceStore.clear() }
            // The disk clear doesn't touch this ViewModel's in-memory state (see
            // ThemeViewModel.reset), which is why a Gold-gated theme kept applying after
            // sign-out.
            themeViewModel.reset()
            // Same as the theme, plus: the launcher icon is a real OS-level setting (see
            // AppIconSwitcher), so it would keep showing a Gold subscriber's choice after
            // sign-out.
            coroutineScope.launch {
                app.appIconPreferenceStore.clear()
                AppIconSwitcher.apply(app, AppIconKey.DEFAULT)
            }
            coroutineScope.launch { app.notificationPreferenceStore.clear() }
            // An invite link not yet dealt with belonged to the account that just left.
            coroutineScope.launch { app.invitePreferenceStore.clearPending() }
            // The widget reads its cached photo (and for Gold, its featured-friend choice and
            // cached Gold status) regardless of sign-in state; without this a friend's private
            // photo and name, or the old account's customization, keeps applying after
            // sign-out.
            // The same routine every other sign-out path uses (see WidgetSession), which also
            // stops the widget's background refresh.
            coroutineScope.launch { WidgetSession.clear(app) }
            // Coil keeps every photo this account viewed (friends' photos, profile pictures)
            // in an on-disk cache that survived sign-out. Clearing the widget's one cached
            // photo while leaving that history was inconsistent. Costs only a re-download of
            // whatever is viewed again.
            SingletonImageLoader.get(app).let { loader ->
                loader.memoryCache?.clear()
                coroutineScope.launch(Dispatchers.IO) { loader.diskCache?.clear() }
            }
            // Per-account ViewModels (feed, friends, login form...) live in the Activity's
            // ViewModelStore and are retrieved by class/key however often `authenticated`
            // flips; without clearing, a new account would see the previous one's cached
            // data and stale form fields.
            activity.viewModelStore.clear()
            // Must follow clear(), so the login screen gets a LoginViewModel with a live
            // scope (see loginSessionId).
            loginSessionId++
            authenticated = false
            nav.nestedScreen = null
            nav.selectedProfileSubject = null
            // pendingVerification is onCreate's one-time cold-start snapshot, still read by
            // the loginViewModel factory. Left set, every LoginViewModel built after this
            // sign-out was re-primed onto NEEDS_EMAIL_VERIFICATION with the same expired
            // deadline, which made "Try again later" look broken: it signed out, then the new
            // screen immediately showed expired again. Nulling it gives any account reached
            // from here (new sign-up, different sign-in, back to Welcome) a clean start.
            coldStart.pendingVerification = null
        }

        // A 401 means the session expired or is invalid; return to login instead of sitting on
        // a permanent "couldn't load" error.
        LaunchedEffect(Unit) {
            app.networkModule.sessionExpired.collect { onSignOut() }
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
            if (!coldStart.hasSavedSession) return@LaunchedEffect
            app.authRepository.resumeSession().onSuccess { outcome ->
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
                // The widget's background refresh is stopped when an account ends (see
                // WidgetSession); this starts it again on sign-in, without waiting for the next
                // app launch. Safe to call repeatedly.
                WidgetUpdateWorker.schedule(app)
                val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                if (token != null) app.authRepository.registerDeviceToken(token)
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
                isGoldMember = app.subscriptionRepository.isGoldMemberOrLastKnown()

                // The backend re-checks Google only when verifyPurchase is called; it doesn't
                // notice a renewal on its own, so its stored expiresAt can lapse though Play
                // renewed. This is the same reconciliation EmberGoldViewModel.refresh() does
                // for a reinstall or new device, so a subscriber who never reopens the Gold
                // screen doesn't see Gold vanish app-wide at their first renewal.
                if (!isGoldMember) {
                    app.billingManager.findActivePurchase()?.let { existing ->
                        app.subscriptionRepository.verifyPurchase(existing.productId, existing.purchaseToken)
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
            SignedInShell(
                app = app,
                nav = nav,
                coldStart = coldStart,
                themeViewModel = themeViewModel,
                widgetPreferenceStore = widgetPreferenceStore,
                widgetFeaturedFriendIds = widgetFeaturedFriendIds,
                isGoldMember = isGoldMember,
                onGoldActivated = {
                    // The purchase already updated SubscriptionRepository's caches; this pulls
                    // the "you're Gold now" answer into the app-wide state other screens
                    // read before they'd re-check.
                    isGoldMember = true
                    coroutineScope.launch { widgetPreferenceStore.setCachedIsGoldMember(true) }
                    themeViewModel.reload()
                },
                onPreviewTheme = { previewThemeKey = it },
                onSignOut = onSignOut,
                coroutineScope = coroutineScope,
                pendingNotificationIntent = pendingNotificationIntent,
                onNotificationIntentHandled = onNotificationIntentHandled,
            )
        }
    }
}
