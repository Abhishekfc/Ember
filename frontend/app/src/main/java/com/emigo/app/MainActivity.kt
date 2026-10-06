package com.emigo.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.emigo.app.data.FirstPhotoPreloader
import com.emigo.app.data.local.LocalListCache
import com.emigo.app.data.remote.dto.FeedItem
import com.emigo.app.data.remote.dto.MemoryPhotoDto
import com.emigo.app.data.remote.dto.UserProfileDto
import com.emigo.app.ui.home.InitialHomeCache
import com.emigo.app.ui.navigation.ColdStartSnapshot
import com.emigo.app.ui.navigation.EmberRoot
import com.emigo.app.ui.theme.EmberBackground
import com.emigo.app.ui.theme.emberThemeDefinition
import com.emigo.app.widget.WidgetUpdateWorker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking

/** The app's only Activity. Sets up the window, reads what the first frame needs, then hands
 * everything to [EmberRoot]. */
class MainActivity : ComponentActivity() {

    // Hoisted to EmberApplication so they're process-wide singletons that survive this Activity
    // being recreated by a config change, instead of each recreation wiring ViewModels to an
    // orphaned NetworkModule.
    private val emberApplication get() = application as EmberApplication
    private val networkModule get() = emberApplication.networkModule
    private val themePreferenceStore get() = emberApplication.themePreferenceStore
    private val localListCache get() = emberApplication.localListCache

    // Compose state so the LaunchedEffect in SignedInShell re-runs on change. Set from the latest
    // launch intent (onCreate on a cold start, onNewIntent when singleTask reuses the running
    // Activity) and cleared once acted on, so a notification tap isn't processed twice across a
    // recomposition.
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
        // Sign-out resets it (see ColdStartSnapshot), otherwise signing into a different account
        // in the same process seeded the new HomeViewModel with the previous account's snapshot
        // until a later fetch overwrote it.
        val initialHomeCache = runBlocking {
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
        // Created once, outside setContent, so a recomposition can never replace it.
        val coldStart = ColdStartSnapshot(
            hasSavedSession = hasSavedSession,
            pendingVerification = pendingVerification,
            initialHomeCache = initialHomeCache,
        )
        setContent {
            EmberRoot(
                activity = this,
                app = emberApplication,
                coldStart = coldStart,
                pendingNotificationIntent = { pendingNotificationIntent },
                onNotificationIntentHandled = { pendingNotificationIntent = null },
            )
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
