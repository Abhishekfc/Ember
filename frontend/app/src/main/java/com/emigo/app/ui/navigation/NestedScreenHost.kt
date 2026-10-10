package com.emigo.app.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emigo.app.EmberApplication
import com.emigo.app.core.findActivity
import com.emigo.app.ui.activity.ActivityScreen
import com.emigo.app.ui.activity.ActivityViewModel
import com.emigo.app.ui.camera.CameraViewModel
import com.emigo.app.ui.camera.RecipientPickerScreen
import com.emigo.app.ui.camera.RecipientPickerViewModel
import com.emigo.app.ui.camera.SentPhotosScreen
import com.emigo.app.ui.camera.SentPhotosViewModel
import com.emigo.app.ui.components.NavDestination
import com.emigo.app.ui.friends.FindPeopleScreen
import com.emigo.app.ui.friends.FindPeopleViewModel
import com.emigo.app.ui.friends.FriendProfileScreen
import com.emigo.app.ui.friends.FriendProfileViewModel
import com.emigo.app.ui.friends.FriendsViewModel
import com.emigo.app.ui.friends.ProfileSubject
import com.emigo.app.ui.home.HomeViewModel
import com.emigo.app.ui.profile.MyProfileScreen
import com.emigo.app.ui.profile.MyProfileViewModel
import com.emigo.app.ui.settings.BlockedUsersScreen
import com.emigo.app.ui.settings.BlockedUsersViewModel
import com.emigo.app.ui.settings.OtherSettingsScreen
import com.emigo.app.ui.settings.WidgetSettingsScreen
import com.emigo.app.ui.settings.WidgetSettingsViewModel
import com.emigo.app.ui.theme.ThemeKey
import com.emigo.app.ui.theme.ThemeScreen
import com.emigo.app.ui.theme.ThemeViewModel
import com.emigo.app.widget.WidgetPreferenceStore
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Shows whichever nested screen is open (see [NestedScreen]), the recipient picker, or, when
 * neither is, [mainTabs] (the pager). Exactly one of these is composed at a time. The Gold screen
 * isn't a branch here: [SignedInShell] draws it on top of whatever this shows. */
@Composable
internal fun NestedScreenHost(
    app: EmberApplication,
    nav: AppNavState,
    shell: ShellState,
    coldStart: ColdStartSnapshot,
    themeViewModel: ThemeViewModel,
    homeViewModel: HomeViewModel,
    friendsViewModel: FriendsViewModel,
    activityViewModel: ActivityViewModel,
    cameraViewModel: CameraViewModel,
    widgetPreferenceStore: WidgetPreferenceStore,
    hazeState: HazeState,
    coroutineScope: CoroutineScope,
    onPreviewTheme: (ThemeKey?) -> Unit,
    onSignOut: () -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onCameraClick: () -> Unit,
    onCloseFriendProfile: () -> Unit,
    resolveActorSubject: (String) -> ProfileSubject?,
    mainTabs: @Composable () -> Unit,
) {
    // Pages slide in from the right and back out to the right, like the iPhone, instead of
    // appearing at once. The page being shown is described by a value (HostScreen), not read live
    // from `nav` inside each branch, so a page that is leaving keeps drawing what it showed
    // (a profile's person is cleared from `nav` the moment the profile closes).
    val target = hostScreenFor(nav.nestedScreen, nav.selectedProfileSubject, nav.friendProfileReturnTo, shell.showRecipientPicker)
    // The Gold page is drawn over this host (see SignedInShell) with its own slide-up. Opening it
    // from another page (Theme, Widget settings) swaps that page away; that swap stays instant, so
    // the old page doesn't slide off behind Gold's own animation.
    val goldIsOpening = nav.nestedScreen == NestedScreen.GOLD
    AnimatedContent(
        targetState = target,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (goldIsOpening) ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
            else pageTransition(initialState, targetState)
        },
        // Same page, no transition: e.g. a profile updating in place after a request is accepted.
        contentKey = { it.key },
        label = "page",
    ) { screen ->
    val shown = (screen as? HostScreen.Nested)?.screen
    when {
        shown == NestedScreen.PROFILE -> {
            val myProfileViewModel: MyProfileViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        MyProfileViewModel(
                            app.stringProvider,
                            app.userRepository,
                            app.localListCache,
                            initialProfile = coldStart.initialHomeCache.profile,
                            onProfileUpdated = { profile ->
                                coroutineScope.launch { app.networkModule.tokenStore.saveDisplayName(profile.displayName) }
                                homeViewModel.applyProfileUpdate(profile)
                            },
                        )
                    }
                },
            )
            MyProfileScreen(viewModel = myProfileViewModel, onClose = { nav.nestedScreen = null })
        }

        shown == NestedScreen.THEME -> ThemeScreen(
            viewModel = themeViewModel,
            onBack = { nav.nestedScreen = null },
            onPreview = onPreviewTheme,
            onUpgradeToGold = { nav.nestedScreen = NestedScreen.GOLD },
        )

        // NestedScreen.GOLD isn't a branch here; SignedInShell draws it on top of this.

        // Activity is reached from the bell in Home's header (see HomeScreen's
        // onActivityClick), not a dock tab or swipe. onCameraClick and
        // onNavigateToFriends must close this nested screen and move the pager (the
        // pattern FriendProfileScreen's onSendPhotoClick uses): closing alone would
        // leave the pager on its current page underneath.
        shown == NestedScreen.ACTIVITY -> ActivityScreen(
            viewModel = activityViewModel,
            onCameraClick = {
                nav.nestedScreen = null
                onCameraClick()
            },
            onNavigateToFriends = {
                nav.nestedScreen = null
                onNavigate(NavDestination.FRIENDS)
            },
            // Both go through the same resolver, so "is this tappable" and "what does
            // it open" can't disagree.
            canOpenActorProfile = { actorId -> resolveActorSubject(actorId) != null },
            onOpenActorProfile = { actorId ->
                resolveActorSubject(actorId)?.let { subject ->
                    nav.selectedProfileSubject = subject
                    nav.friendProfileReturnTo = NestedScreen.ACTIVITY
                    nav.nestedScreen = NestedScreen.FRIEND_PROFILE
                }
            },
            hazeState = hazeState,
        )

        shown == NestedScreen.WIDGET_SETTINGS -> {
            val widgetSettingsViewModel: WidgetSettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        WidgetSettingsViewModel(app.stringProvider, app.friendRepository, app.subscriptionRepository, widgetPreferenceStore)
                    }
                },
            )
            WidgetSettingsScreen(
                viewModel = widgetSettingsViewModel,
                onClose = { nav.nestedScreen = null },
                onUpgradeToGold = { nav.nestedScreen = NestedScreen.GOLD },
            )
        }

        shown == NestedScreen.BLOCKED_USERS -> {
            val blockedUsersViewModel: BlockedUsersViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { BlockedUsersViewModel(app.stringProvider, app.safetyRepository) }
                },
            )
            BlockedUsersScreen(
                viewModel = blockedUsersViewModel,
                onClose = { nav.nestedScreen = null },
            )
        }

        shown == NestedScreen.SENT_PHOTOS -> {
            val sentPhotosViewModel: SentPhotosViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SentPhotosViewModel(app.stringProvider, app.photoRepository) }
                },
            )
            SentPhotosScreen(
                viewModel = sentPhotosViewModel,
                onBack = { nav.nestedScreen = null },
            )
        }

        shown == NestedScreen.OTHER_SETTINGS -> {
            val activity = LocalContext.current.findActivity()
            OtherSettingsScreen(
                onClose = { nav.nestedScreen = null },
                onDeleteAccount = { app.userRepository.deleteAccount() },
                // Same local cleanup and return to login as a manual sign-out; no
                // account is left for the cached state to belong to.
                onAccountDeleted = onSignOut,
                // Gold members never see ads, so they are never asked and never have a choice to
                // change; the ad services aren't even contacted for them.
                isPrivacyOptionsRequired = {
                    activity != null &&
                        !app.subscriptionRepository.isGoldMemberOrLastKnown() &&
                        app.adConsent.isPrivacyOptionsRequired(activity)
                },
                onOpenPrivacyOptions = { activity?.let(app.adConsent::showPrivacyOptions) },
            )
        }

        shown == NestedScreen.FIND_PEOPLE -> {
            val findPeopleViewModel: FindPeopleViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { FindPeopleViewModel(app.stringProvider, app.friendRepository) }
                },
            )
            FindPeopleScreen(
                viewModel = findPeopleViewModel,
                onBack = { nav.nestedScreen = null },
                onResultClick = { result ->
                    nav.selectedProfileSubject = ProfileSubject.SearchResult(result)
                    nav.friendProfileReturnTo = NestedScreen.FIND_PEOPLE
                    nav.nestedScreen = NestedScreen.FRIEND_PROFILE
                },
            )
        }

        screen is HostScreen.FriendProfile -> {
            // From the screen value, not `nav`: while this page slides away the person has
            // already been cleared from `nav`.
            val subject = screen.subject
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
                    initializer { FriendProfileViewModel(app.stringProvider, app.friendRepository, app.safetyRepository, subject) }
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
                    app.notifyFriendsChanged()
                    nav.nestedScreen = null
                    nav.selectedProfileSubject = null
                },
                onAccepted = { newFriend ->
                    friendsViewModel.addFriendLocally(newFriend)
                    app.notifyFriendsChanged()
                    nav.nestedScreen = null
                    nav.selectedProfileSubject = null
                },
                onRejected = {
                    subject.friendshipId?.let { friendsViewModel.removePendingRequestLocally(it) }
                    nav.nestedScreen = null
                    nav.selectedProfileSubject = null
                },
                onBlocked = {
                    // Same local-list update as onRemoved: blocking also deletes any
                    // friendship server-side (see BlockService), and a pending
                    // request between the two is invalid once blocked.
                    subject.friendshipId?.let {
                        friendsViewModel.removeFriendLocally(it)
                        friendsViewModel.removePendingRequestLocally(it)
                    }
                    app.notifyFriendsChanged()
                    nav.nestedScreen = null
                    nav.selectedProfileSubject = null
                },
            )
        }

        screen is HostScreen.Picker -> {
            val recipientPickerViewModel: RecipientPickerViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        RecipientPickerViewModel(
                            app.stringProvider,
                            app.friendRepository,
                            app.localListCache,
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
                app.friendsChangedEvents.collect { recipientPickerViewModel.loadFriends() }
            }
            RecipientPickerScreen(
                viewModel = recipientPickerViewModel,
                onClose = { shell.showRecipientPicker = false },
                onConfirm = { ids ->
                    // The picker's list is the fresh one: the camera's can be missing a friend
                    // added after it loaded, which left Send disabled for the chosen friend.
                    cameraViewModel.setSelectedRecipients(ids, recipientPickerViewModel.friends)
                    shell.showRecipientPicker = false
                },
                onAddFriend = {
                    shell.showRecipientPicker = false
                    nav.nestedScreen = NestedScreen.FIND_PEOPLE
                },
            )
        }

        else -> mainTabs()
    }
    }
}

/** The iPhone-style push and pop. A push slides the new page in from the right over the old one,
 * which drifts a little to the left and dims; a pop slides the top page out to the right and
 * brings the one underneath back. The page being revealed or covered sits underneath, so the
 * moving page is always the one on top. No transition runs at app start. */
private fun AnimatedContentTransitionScope<HostScreen>.pageTransition(from: HostScreen, to: HostScreen): ContentTransform {
    val push = isPush(from, to)
    val slide = tween<IntOffset>(durationMillis = PAGE_TRANSITION_MILLIS, easing = PageEasing)
    val fade = tween<Float>(durationMillis = PAGE_TRANSITION_MILLIS, easing = PageEasing)
    return if (push) {
        ContentTransform(
            targetContentEnter = slideInHorizontally(slide) { width -> width },
            initialContentExit = slideOutHorizontally(slide) { width -> -width / 4 } + fadeOut(fade, targetAlpha = 0.6f),
            targetContentZIndex = 1f,
            sizeTransform = null,
        )
    } else {
        ContentTransform(
            targetContentEnter = slideInHorizontally(slide) { width -> -width / 4 } + fadeIn(fade, initialAlpha = 0.6f),
            initialContentExit = slideOutHorizontally(slide) { width -> width },
            targetContentZIndex = 0f,
            sizeTransform = null,
        )
    }
}

private const val PAGE_TRANSITION_MILLIS = 340

/** Fast start, long gentle stop: close to the curve the iPhone uses for a push. */
private val PageEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
