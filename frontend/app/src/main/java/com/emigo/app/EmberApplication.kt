package com.emigo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.emigo.app.ads.AdConsent
import com.emigo.app.ads.AdMobRewardedAds
import com.emigo.app.ads.GalleryUnlock
import com.emigo.app.ads.RemoteAdSettings
import com.emigo.app.ads.RewardedAds
import com.emigo.app.ads.SharedPrefsGalleryUnlockStorage
import com.emigo.app.invite.InstallReferrerReader
import com.emigo.app.invite.InviteReferral
import com.emigo.app.widget.WidgetSession
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.emigo.app.core.StringProvider
import com.emigo.app.data.billing.BillingManager
import com.emigo.app.data.repository.ActivityRepository
import com.emigo.app.data.repository.AuthRepository
import com.emigo.app.data.repository.FriendRepository
import com.emigo.app.data.repository.PhotoRepository
import com.emigo.app.data.repository.SafetyRepository
import com.emigo.app.data.repository.SubscriptionRepository
import com.emigo.app.data.repository.UserRepository
import com.emigo.app.data.local.CameraHintPreferenceStore
import com.emigo.app.data.local.InvitePreferenceStore
import com.emigo.app.data.local.LocalListCache
import com.emigo.app.data.local.NotificationPreferenceStore
import com.emigo.app.data.local.AppIconPreferenceStore
import com.emigo.app.data.local.ThemePreferenceStore
import com.emigo.app.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okio.Path.Companion.toOkioPath

/** Channel a NEW_PHOTO push is posted to when the device is on Android 8+ (required there before
 * any notification can be shown at all) — see EmberFirebaseMessagingService, the only poster. */
const val NEW_PHOTO_NOTIFICATION_CHANNEL_ID = "new_photo"

/** Separate channel from [NEW_PHOTO_NOTIFICATION_CHANNEL_ID] — a broken-streak alert is a
 * meaningfully different kind of notification (occasional, action-driven) from "a friend sent you
 * a photo" (frequent, routine), and a user should be able to mute one without the other from
 * system notification settings rather than the two being permanently bundled together. */
const val STREAK_NOTIFICATION_CHANNEL_ID = "streak"

/** Registered as this app's `<application android:name>` so Coil (image loading) picks up
 * [newImageLoader] instead of its own zero-config default, and so [networkModule] and every
 * repository built from it are true process-wide singletons rather than tied to whichever
 * `MainActivity` instance happens to be alive.
 *
 * Without this, Coil's default disk cache lives under `FileSystem.SYSTEM_TEMPORARY_DIRECTORY` —
 * not `context.cacheDir` — which is not guaranteed to actually persist across a full app-process
 * restart the way a normal Android app-private cache directory is. That's what made every photo
 * (Memories thumbnails, the profile picture) look like it was re-fetched over the network from
 * scratch on every restart: the disk cache itself was effectively starting empty each time, not
 * just the in-memory one (which is *supposed* to be wiped on a process restart — that part was
 * never the problem). Pointing it at [Application.getCacheDir] explicitly fixes that.
 *
 * [networkModule] used to be a `by lazy` property of `MainActivity` itself. Any config change
 * that isn't covered by the manifest (system dark/light toggle, font-scale change, display
 * density change, multi-window/foldable resize — `MainActivity` only declares
 * `screenOrientation`, nothing else) destroys and recreates the Activity, which used to build a
 * *new* `NetworkModule` (new OkHttpClient, new `sessionExpired` flow) — but `ViewModelStore`
 * survives that recreation, so every already-created ViewModel kept its repository wired to the
 * *old*, now-dead instance. The result: session-expiry auto-sign-out silently stopped working
 * for the rest of the process's life the moment a single qualifying config change happened after
 * login — every screen just showed its generic error forever, with no way back to the login
 * screen short of force-killing the app. Living here instead means there's only ever one
 * `NetworkModule` for the whole process, so this class of bug can't recur. */
class EmberApplication : Application(), SingletonImageLoader.Factory {

    val networkModule by lazy { NetworkModule(this) }
    val authRepository by lazy { AuthRepository(networkModule.api, networkModule.tokenStore) }
    val photoRepository by lazy { PhotoRepository(networkModule.api) }
    val friendRepository by lazy { FriendRepository(networkModule.api) }
    val activityRepository by lazy { ActivityRepository(networkModule.api) }
    val userRepository by lazy { UserRepository(networkModule.api) }
    val subscriptionRepository by lazy { SubscriptionRepository(networkModule.api, this) }
    // One Play Billing connection for the whole process (see BillingManager's own doc comment) —
    // the Gold paywall is the only consumer, but a singleton keeps the connection warm between
    // visits and matches how every other repository here is scoped.
    val billingManager by lazy { BillingManager(this) }
    val safetyRepository by lazy { SafetyRepository(networkModule.api) }
    val stringProvider by lazy { StringProvider(this) }
    val themePreferenceStore by lazy { ThemePreferenceStore(this) }
    val appIconPreferenceStore by lazy { AppIconPreferenceStore(this) }
    val notificationPreferenceStore by lazy { NotificationPreferenceStore(this) }
    val localListCache by lazy { LocalListCache(this) }
    val cameraHintPreferenceStore by lazy { CameraHintPreferenceStore(this) }

    // "Add @ann?" after installing from ann's invite link (see invite/).
    val invitePreferenceStore by lazy { InvitePreferenceStore(this) }
    val inviteReferral by lazy {
        InviteReferral(
            store = invitePreferenceStore,
            readInstallReferrer = { InstallReferrerReader(this).read() },
            search = friendRepository::searchUsers,
            sendRequest = { userId -> friendRepository.sendFriendRequest(userId).map { } },
        )
    }

    // Rewarded ads (see ads/). All lazy, and the ads SDK itself only starts when an ad is first
    // asked for, so someone with Emigo Gold, who never sees one, never loads it.
    val adConsent by lazy { AdConsent(this) }
    // The ad rules that can change without an app update (gallery ads per photo and per day, and
    // the ads on/off safety switch); see ads/RemoteAdSettings.kt.
    val adSettings by lazy { RemoteAdSettings() }
    val rewardedAds: RewardedAds by lazy { AdMobRewardedAds(this, adConsent, adSettings) }
    val galleryUnlock by lazy { GalleryUnlock(SharedPrefsGalleryUnlockStorage(this), adSettings) }

    // Bridges EmberFirebaseMessagingService (a separate Android component with no direct
    // reference to whatever ViewModels/Activity happen to be alive) to a live HomeViewModel —
    // same pattern as NetworkModule.sessionExpired. A silent background NEW_PHOTO push always
    // updates the widget directly (see WidgetPhotoSync.syncFromPush, which needs no app-alive
    // state at all), but only a *live* HomeViewModel can also refresh its own syncedFeedItems;
    // this is how it finds out to do so, without either side needing to know about the other.
    private val _newPhotoPushEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val newPhotoPushEvents: SharedFlow<Unit> = _newPhotoPushEvents.asSharedFlow()

    fun notifyNewPhotoPush() {
        _newPhotoPushEvents.tryEmit(Unit)
    }

    // Same bridge, same reasoning, for the other direction: PendingSendWorker runs a queued send
    // in the background — possibly long after CameraScreen queued it, possibly with the app fully
    // killed in between — and has no reference of its own to a live HomeViewModel either. Kept
    // separate from newPhotoPushEvents (not reused) because a friend's incoming push only ever
    // needs Feed refreshed; a send finishing is *my own* new photo, so it needs Memories refreshed
    // too — collapsing the two into one event would refresh the wrong things for one of them.
    private val _photoSendCompletedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val photoSendCompletedEvents: SharedFlow<Unit> = _photoSendCompletedEvents.asSharedFlow()

    fun notifyPhotoSendCompleted() {
        _photoSendCompletedEvents.tryEmit(Unit)
    }

    // Same bridge again, this time for "the friend graph changed" (a request accepted, a friend
    // removed or blocked). Several long-lived ViewModels each keep their own independent copy of
    // "who am I friends with" (CameraViewModel's recipient list, RecipientPickerViewModel, Friends'
    // own tab) fetched once and never refetched on their own — without this, accepting a request
    // from one screen left every other screen's own copy stale until the app restarted, since
    // nothing told them anything had changed.
    private val _friendsChangedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val friendsChangedEvents: SharedFlow<Unit> = _friendsChangedEvents.asSharedFlow()

    fun notifyFriendsChanged() {
        _friendsChangedEvents.tryEmit(Unit)
    }

    override fun onCreate() {
        super.onCreate()
        stopWidgetWhenSignedOut()
        // Fetches the latest ad rules in the background, at most once per fetch interval (an hour),
        // so nothing waits on it and a failure just keeps the values already held.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { adSettings.refresh() }
        // Notification channels are a one-time, idempotent registration — safe (and normal) to
        // call on every process start rather than checking whether it already exists.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NEW_PHOTO_NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_new_photos_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.channel_new_photos_description) }
            val streakChannel = NotificationChannel(
                STREAK_NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_streaks_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.channel_streaks_description) }
            getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, streakChannel))
        }
    }

    /** Whenever the account signs out, whichever way it happens (the user, a background job finding
     * the session dead, or Firebase ending a session by itself after the password was changed on
     * another phone), the widget stops with it. Registered at every process start, because that
     * last case can happen while the app has no screen open. Only reacts to going from signed in
     * to signed out, so a phone that was never signed in does nothing. See [WidgetSession]. */
    private fun stopWidgetWhenSignedOut() {
        val auth = FirebaseAuth.getInstance()
        var hadUser = auth.currentUser != null
        auth.addAuthStateListener { current ->
            val hasUser = current.currentUser != null
            if (hadUser && !hasUser) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { WidgetSession.clear(this@EmberApplication) }
            }
            hadUser = hasUser
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            // Explicit rather than relying on Coil's own implicit default (which is this same
            // 25% figure) — so a decoded photo swiped to once stays in memory and reappearing
            // (swipe back, reopen Home) is instant with no re-decode, and so the size is a
            // documented choice here rather than something the next reader has to go verify.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            // Applies to every AsyncImage app-wide unless a request overrides it — the rare
            // case that's neither preloaded nor already cached (e.g. the very first cold start,
            // or a genuinely brand-new photo mid-session) fades in instead of popping straight
            // from blank to loaded.
            .crossfade(true)
            .build()
    }
}
