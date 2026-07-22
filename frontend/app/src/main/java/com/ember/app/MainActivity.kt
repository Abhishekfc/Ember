package com.ember.app

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.ember.app.data.ActivityRepository
import com.ember.app.data.AuthRepository
import com.ember.app.data.FriendRepository
import com.ember.app.data.PhotoRepository
import com.ember.app.data.SubscriptionRepository
import com.ember.app.data.UserRepository
import com.ember.app.data.local.NotificationPreferenceStore
import com.ember.app.data.local.SeenPhotoStore
import com.ember.app.data.local.ThemePreferenceStore
import com.ember.app.data.remote.NetworkModule
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.ui.activity.ActivityScreen
import com.ember.app.ui.activity.ActivityViewModel
import com.ember.app.ui.auth.LoginScreen
import com.ember.app.ui.auth.LoginViewModel
import com.ember.app.ui.camera.CameraScreen
import com.ember.app.ui.camera.CameraViewModel
import com.ember.app.ui.camera.RecipientPickerScreen
import com.ember.app.ui.camera.RecipientPickerViewModel
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.friends.FindPeopleScreen
import com.ember.app.ui.friends.FindPeopleViewModel
import com.ember.app.ui.friends.FriendProfileScreen
import com.ember.app.ui.friends.FriendProfileViewModel
import com.ember.app.ui.friends.FriendsScreen
import com.ember.app.ui.friends.FriendsViewModel
import com.ember.app.ui.home.HomeScreen
import com.ember.app.ui.home.HomeViewModel
import com.ember.app.ui.profile.MyProfileScreen
import com.ember.app.ui.profile.MyProfileViewModel
import com.ember.app.ui.settings.EmberGoldScreen
import com.ember.app.ui.settings.SettingsScreen
import com.ember.app.ui.theme.EmberAppTheme
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.ThemeKey
import com.ember.app.ui.theme.ThemeScreen
import com.ember.app.ui.theme.ThemeViewModel
import com.ember.app.widget.WidgetPhotoSync
import com.ember.app.widget.WidgetUpdateWorker
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/** Screens reached from within a tab (Settings -> Theme, Friends -> Find People / Friend
 * Profile) or from the raised center nav button (Camera) rather than from the bottom nav tabs
 * directly. Kept separate from the active tab so back navigation can pop just the nested
 * screen without losing which tab you were on. */
private enum class NestedScreen { THEME, FIND_PEOPLE, FRIEND_PROFILE, CAMERA, PROFILE, GOLD }

class MainActivity : ComponentActivity() {

    private val networkModule by lazy { NetworkModule(applicationContext) }
    private val authRepository by lazy { AuthRepository(networkModule.api, networkModule.tokenStore) }
    private val photoRepository by lazy { PhotoRepository(networkModule.api) }
    private val friendRepository by lazy { FriendRepository(networkModule.api) }
    private val activityRepository by lazy { ActivityRepository(networkModule.api) }
    private val userRepository by lazy { UserRepository(networkModule.api) }
    private val subscriptionRepository by lazy { SubscriptionRepository(networkModule.api) }
    private val themePreferenceStore by lazy { ThemePreferenceStore(applicationContext) }
    private val seenPhotoStore by lazy { SeenPhotoStore(applicationContext) }
    private val notificationPreferenceStore by lazy { NotificationPreferenceStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the widget's background refresh alive even across reinstalls that wiped the
        // schedule; KEEP policy makes this a no-op when it's already queued.
        WidgetUpdateWorker.schedule(applicationContext)
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
                var activeTab by remember { mutableStateOf(NavDestination.HOME) }
                var nestedScreen by remember { mutableStateOf<NestedScreen?>(null) }
                var selectedFriend by remember { mutableStateOf<FriendSummaryDto?>(null) }
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
                    // All per-account ViewModels (home feed, friends list, login form, etc.)
                    // live in the Activity's ViewModelStore and are normally retrieved by
                    // class/key regardless of how many times `authenticated` flips — without
                    // clearing here, signing into a different account would keep showing the
                    // previous account's cached feed, friends, and stale form fields.
                    viewModelStore.clear()
                    authenticated = false
                    activeTab = NavDestination.HOME
                    nestedScreen = null
                    selectedFriend = null
                }

                // The backend issues short-lived JWTs with no refresh flow yet, so a session
                // will eventually 401 on its own — without this, the app would sit on a
                // permanently broken "couldn't load" error instead of returning to login.
                LaunchedEffect(Unit) {
                    networkModule.sessionExpired.collect { onSignOut() }
                }

                if (!sessionChecked) {
                    Box(modifier = Modifier.fillMaxSize().background(EmberTheme.colors.background.asBrush(Size.Zero)))
                } else if (!authenticated) {
                    LoginScreen(viewModel = loginViewModel, onAuthenticated = { authenticated = true })
                } else {
                    // Swiping/pressing back should always retrace the last navigation step
                    // instead of falling through to the system default (which closes the app):
                    // first close any nested screen, then return to Home before actually exiting.
                    BackHandler(enabled = nestedScreen != null || activeTab != NavDestination.HOME) {
                        if (nestedScreen != null) {
                            nestedScreen = null
                        } else {
                            activeTab = NavDestination.HOME
                        }
                    }

                    val onNavigate: (NavDestination) -> Unit = { destination ->
                        activeTab = destination
                        nestedScreen = null
                    }
                    val onCameraClick = { nestedScreen = NestedScreen.CAMERA }

                    // Hoisted rather than let each tab screen create its own: real-time backdrop
                    // blur needs to set up a GPU render-effect pipeline (shader compile, capture
                    // buffers) the first time it runs. Each tab creating its own HazeState meant
                    // that pipeline was torn down and rebuilt from scratch on every single tab
                    // switch — a real, consistent stutter on every nav tap, not specific to any
                    // one screen. One shared instance keeps it alive across navigation.
                    val hazeState = rememberHazeState()

                    // Hoisted (rather than declared inside their respective tab branches below)
                    // so they survive navigating into nested screens and back, and so they can
                    // be refreshed from elsewhere: FriendsViewModel after a friend is removed,
                    // HomeViewModel after a photo is sent (streaks can change on send, not just
                    // receive).
                    val friendsViewModel: FriendsViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { FriendsViewModel(friendRepository) }
                        },
                    )
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                HomeViewModel(
                                    photoRepository,
                                    networkModule.tokenStore,
                                    userRepository,
                                    seenPhotoStore,
                                    onFeedLoaded = { items ->
                                        coroutineScope.launch { WidgetPhotoSync.sync(applicationContext, items) }
                                    },
                                )
                            }
                        },
                    )
                    // Also hoisted, for the same reason: created here means its fetch starts as
                    // soon as the app opens, in the background, rather than only starting the
                    // moment the user first taps the Activity tab — that lazy-create pattern is
                    // what made Activity specifically feel slower to open than Home or Friends,
                    // whose ViewModels (and therefore their network calls) were already hoisted.
                    val activityViewModel: ActivityViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { ActivityViewModel(activityRepository) }
                        },
                    )

                    when {
                        nestedScreen == NestedScreen.CAMERA -> {
                            val cameraViewModel: CameraViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { CameraViewModel(friendRepository, photoRepository, subscriptionRepository) }
                                },
                            )
                            var showRecipientPicker by remember { mutableStateOf(false) }
                            BackHandler(enabled = showRecipientPicker) { showRecipientPicker = false }

                            if (showRecipientPicker) {
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
                            } else {
                                CameraScreen(
                                    viewModel = cameraViewModel,
                                    // cameraViewModel is scoped to this Activity, not recreated
                                    // per visit — without discarding here, a capture the user
                                    // closed out of (rather than sent) would still be sitting
                                    // there in review, unreachable-looking-fresh, the next time
                                    // the camera reopens.
                                    onClose = { cameraViewModel.discardCapture(); nestedScreen = null },
                                    onOpenRecipientPicker = { showRecipientPicker = true },
                                    onUpgradeToGold = { nestedScreen = NestedScreen.GOLD },
                                    onSent = {
                                        nestedScreen = null
                                        homeViewModel.loadFeed()
                                    },
                                )
                            }
                        }

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
                            FindPeopleScreen(viewModel = findPeopleViewModel)
                        }

                        nestedScreen == NestedScreen.FRIEND_PROFILE && selectedFriend != null -> {
                            val friend = selectedFriend!!
                            val friendProfileViewModel: FriendProfileViewModel = viewModel(
                                key = friend.friendshipId,
                                factory = viewModelFactory {
                                    initializer { FriendProfileViewModel(friendRepository, friend) }
                                },
                            )
                            FriendProfileScreen(
                                viewModel = friendProfileViewModel,
                                onBack = {
                                    nestedScreen = null
                                    selectedFriend = null
                                },
                                onSendPhotoClick = onCameraClick,
                                onRemoved = {
                                    nestedScreen = null
                                    selectedFriend = null
                                    friendsViewModel.loadFriends()
                                },
                            )
                        }

                        activeTab == NavDestination.SETTINGS -> {
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
                                onNavigate = onNavigate,
                                onCameraClick = onCameraClick,
                                onProfileClick = { nestedScreen = NestedScreen.PROFILE },
                                onThemeClick = { nestedScreen = NestedScreen.THEME },
                                onGoldClick = { nestedScreen = NestedScreen.GOLD },
                                onSignOut = onSignOut,
                                hazeState = hazeState,
                            )
                        }

                        activeTab == NavDestination.ACTIVITY -> {
                            ActivityScreen(
                                viewModel = activityViewModel,
                                onNavigate = onNavigate,
                                onCameraClick = onCameraClick,
                                hazeState = hazeState,
                            )
                        }

                        activeTab == NavDestination.FRIENDS -> FriendsScreen(
                            viewModel = friendsViewModel,
                            onNavigate = onNavigate,
                            onCameraClick = onCameraClick,
                            onFindPeopleClick = { nestedScreen = NestedScreen.FIND_PEOPLE },
                            onFriendClick = { friend ->
                                selectedFriend = friend
                                nestedScreen = NestedScreen.FRIEND_PROFILE
                            },
                            hazeState = hazeState,
                        )

                        else -> HomeScreen(
                            viewModel = homeViewModel,
                            onNavigate = onNavigate,
                            onCameraClick = onCameraClick,
                            onAddFriendClick = { nestedScreen = NestedScreen.FIND_PEOPLE },
                            onProfileClick = { nestedScreen = NestedScreen.PROFILE },
                            hazeState = hazeState,
                        )
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
