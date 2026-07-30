package com.ember.app

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
import com.ember.app.data.ActivityRepository
import com.ember.app.data.AuthRepository
import com.ember.app.data.FriendRepository
import com.ember.app.data.PhotoRepository
import com.ember.app.data.SafetyRepository
import com.ember.app.data.SubscriptionRepository
import com.ember.app.data.UserRepository
import com.ember.app.data.local.LocalListCache
import com.ember.app.data.local.NotificationPreferenceStore
import com.ember.app.data.local.ThemePreferenceStore
import com.ember.app.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okio.Path.Companion.toOkioPath

/** Channel a NEW_PHOTO push is posted to when the device is on Android 8+ (required there before
 * any notification can be shown at all) — see EmberFirebaseMessagingService, the only poster. */
const val NEW_PHOTO_NOTIFICATION_CHANNEL_ID = "new_photo"

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
    val safetyRepository by lazy { SafetyRepository(networkModule.api) }
    val themePreferenceStore by lazy { ThemePreferenceStore(this) }
    val notificationPreferenceStore by lazy { NotificationPreferenceStore(this) }
    val localListCache by lazy { LocalListCache(this) }

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

    override fun onCreate() {
        super.onCreate()
        // Notification channels are a one-time, idempotent registration — safe (and normal) to
        // call on every process start rather than checking whether it already exists.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NEW_PHOTO_NOTIFICATION_CHANNEL_ID,
                "New photos",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "A friend sent you a new photo" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
