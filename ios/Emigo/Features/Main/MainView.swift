import SwiftUI

/// The signed-in app: five tabs under a floating dock, with Profile, Activity, Find people and
/// Blocked accounts opening on top. A tab's screen is built the first time it's opened and then
/// kept, so scroll positions and loaded photos survive switching away and back.
struct MainView: View {
    let session: AppSession
    let environment: AppEnvironment

    @State private var path: [MainRoute] = []
    @State private var selection: AppTab
    @State private var openedTabs: Set<AppTab>
    @State private var home: HomeViewModel
    @State private var friends: FriendsViewModel
    @State private var memories: MemoriesViewModel
    @State private var activity: ActivityViewModel
    @State private var camera: CameraViewModel
    @State private var settings: SettingsViewModel

    @Environment(\.theme) private var theme
    @Environment(\.scenePhase) private var scenePhase

    init(environment: AppEnvironment, session: AppSession) {
        self.session = session
        self.environment = environment
        let startTab = AppTab.launchOverride ?? .home
        _path = State(initialValue: MainRoute.launchOverride.map { [$0] } ?? [])
        _selection = State(initialValue: startTab)
        _openedTabs = State(initialValue: [startTab])
        _home = State(initialValue: HomeViewModel(photos: environment.photos, users: environment.users, friends: environment.friends))
        _friends = State(initialValue: FriendsViewModel(repository: environment.friends))
        _memories = State(initialValue: MemoriesViewModel(photos: environment.photos, users: environment.users))
        _activity = State(initialValue: ActivityViewModel(repository: environment.activity))
        _camera = State(initialValue: CameraViewModel(
            friends: environment.friends,
            photos: environment.photos,
            subscription: environment.subscription,
            queue: environment.outbox,
            store: environment.sessionStore
        ))
        _settings = State(initialValue: SettingsViewModel(store: environment.sessionStore))
    }

    private var isGoldMember: Bool { environment.subscription.isGoldMember }

    var body: some View {
        NavigationStack(path: $path) {
            tabs
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: MainRoute.self) { route in
                    destination(for: route)
                }
        }
        .tint(theme.colors.cream)
        .task {
            // Photos waiting to be sent start going out, and the camera and Home hear when one lands.
            environment.outbox.onJobFinished = { id in
                camera.jobFinished(id)
                Task { await home.load() }
            }
            environment.outbox.start()
            // Sets the camera up now (without turning it on), so opening the Camera tab is quick.
            camera.engine.prepare()
            async let gold = environment.subscription.refreshIsGoldMember()
            await activity.load()
            _ = await gold
        }
        // Coming back to the app is the moment new photos and activity have most likely arrived,
        // and a good time to try any photos that couldn't be sent.
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            environment.outbox.retryNow()
            Task {
                await home.load()
                await activity.load()
                await environment.subscription.refreshIsGoldMember()
            }
        }
    }

    private var tabs: some View {
        ZStack {
            theme.colors.background.ignoresSafeArea()

            ForEach(AppTab.allCases) { tab in
                if openedTabs.contains(tab) {
                    page(for: tab)
                        .opacity(selection == tab ? 1 : 0)
                        // Its own quick fade. Without this the page took the dock bubble's slow,
                        // bouncy animation (half a second and more) when you slid across the dock.
                        .animation(.easeOut(duration: 0.12), value: selection)
                        .allowsHitTesting(selection == tab)
                        .accessibilityHidden(selection != tab)
                }
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            TabBar(selection: $selection)
        }
        .onChange(of: selection) { _, tab in openedTabs.insert(tab) }
    }

    @ViewBuilder
    private func page(for tab: AppTab) -> some View {
        switch tab {
        case .memories:
            MemoriesView(model: memories)
        case .home:
            HomeView(
                model: home,
                activityBadge: activity.newCount,
                onOpenCamera: { selection = .camera },
                onOpenProfile: openProfile,
                onOpenActivity: { path.append(.activity) },
                onOpenFindPeople: { path.append(.findPeople) }
            )
        case .camera:
            CameraView(
                model: camera,
                isActive: selection == .camera && path.isEmpty,
                onOpenRecipients: { path.append(.recipientPicker) },
                onOpenSentPhotos: { path.append(.sentPhotos) }
            )
        case .friends:
            FriendsView(
                model: friends,
                onOpenFindPeople: { path.append(.findPeople) },
                onOpenProfile: { path.append(.friendProfile($0)) }
            )
        case .settings:
            SettingsView(
                profile: home.profile,
                isGoldMember: isGoldMember,
                themeName: environment.themes.appliedKey.displayName,
                model: settings,
                onOpenProfile: openProfile,
                onOpenGold: { path.append(.gold) },
                onOpenAppearance: { path.append(.appearance) },
                onOpenBlocked: { path.append(.blockedAccounts) },
                onOpenOther: { path.append(.otherSettings) },
                onLogOut: session.signOut
            )
        }
    }

    @ViewBuilder
    private func destination(for route: MainRoute) -> some View {
        switch route {
        case .profile:
            if let profile = home.profile {
                ProfileScreen(profile: profile, environment: environment, onUpdated: { home.applyProfile($0) })
            }
        case .activity:
            ActivityView(model: activity)
        case .findPeople:
            FindPeopleScreen(
                environment: environment,
                myUsername: home.profile?.username,
                onOpenProfile: { path.append(.friendProfile(.searchResult($0))) }
            )
        case .friendProfile(let subject):
            FriendProfileScreen(
                environment: environment,
                subject: subject,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onSendPhoto: { friendId in
                    camera.setSelectedRecipients([friendId])
                    path.removeAll()
                    selection = .camera
                },
                onChanged: refreshAfterFriendChange
            )
        case .blockedAccounts:
            BlockedAccountsScreen(environment: environment)
        case .gold:
            GoldView(isGoldMember: isGoldMember, profile: home.profile)
        case .appearance:
            AppearanceView(
                themes: environment.themes,
                isGoldMember: isGoldMember,
                onGetGold: { path.append(.gold) }
            )
        case .otherSettings:
            OtherSettingsScreen(environment: environment, onAccountDeleted: session.signOut)
        case .recipientPicker:
            RecipientPickerScreen(
                environment: environment,
                selected: camera.selectedRecipientIds,
                friends: camera.friends,
                onDone: { ids in
                    camera.setSelectedRecipients(ids)
                    if !path.isEmpty { path.removeLast() }
                },
                onFindFriends: { path.append(.findPeople) }
            )
        case .sentPhotos:
            SentPhotosScreen(environment: environment)
        }
    }

    /// A friendship or pin changed on a profile; the lists that show it reload.
    private func refreshAfterFriendChange() {
        Task {
            await friends.load(forceRefresh: true)
            await home.load(forceRefresh: true)
            await camera.refresh()
        }
    }

    /// The profile needs to have loaded; until it has, the tap does nothing rather than opening an empty page.
    private func openProfile() {
        if home.profile != nil { path.append(.profile) } else { Task { await home.load() } }
    }
}

// Each pushed screen owns its view model, created once when the screen first appears.

private struct ProfileScreen: View {
    @State private var model: ProfileViewModel

    init(profile: UserProfile, environment: AppEnvironment, onUpdated: @escaping (UserProfile) -> Void) {
        _model = State(initialValue: ProfileViewModel(
            profile: profile,
            users: environment.users,
            identity: environment.identity,
            onUpdated: onUpdated
        ))
    }

    var body: some View { ProfileView(model: model) }
}

private struct FindPeopleScreen: View {
    @State private var model: FindPeopleViewModel
    let myUsername: String?
    let onOpenProfile: (FriendSearchResult) -> Void

    init(environment: AppEnvironment, myUsername: String?, onOpenProfile: @escaping (FriendSearchResult) -> Void) {
        _model = State(initialValue: FindPeopleViewModel(repository: environment.friends))
        self.myUsername = myUsername
        self.onOpenProfile = onOpenProfile
    }

    var body: some View { FindPeopleView(model: model, myUsername: myUsername, onOpenProfile: onOpenProfile) }
}

private struct FriendProfileScreen: View {
    @State private var model: FriendProfileViewModel
    let onBack: () -> Void
    let onSendPhoto: (String) -> Void
    let onChanged: () -> Void

    init(
        environment: AppEnvironment,
        subject: ProfileSubject,
        onBack: @escaping () -> Void,
        onSendPhoto: @escaping (String) -> Void,
        onChanged: @escaping () -> Void
    ) {
        _model = State(initialValue: FriendProfileViewModel(subject: subject, friends: environment.friends, safety: environment.safety))
        self.onBack = onBack
        self.onSendPhoto = onSendPhoto
        self.onChanged = onChanged
    }

    var body: some View {
        FriendProfileView(model: model, onBack: onBack, onSendPhoto: onSendPhoto, onChanged: onChanged, onClose: onBack)
    }
}

private struct RecipientPickerScreen: View {
    @State private var model: RecipientPickerViewModel
    let onDone: ([String]) -> Void
    let onFindFriends: () -> Void

    init(
        environment: AppEnvironment,
        selected: [String],
        friends: [FriendSummary],
        onDone: @escaping ([String]) -> Void,
        onFindFriends: @escaping () -> Void
    ) {
        _model = State(initialValue: RecipientPickerViewModel(
            repository: environment.friends,
            store: environment.sessionStore,
            initialSelected: selected,
            initialFriends: friends
        ))
        self.onDone = onDone
        self.onFindFriends = onFindFriends
    }

    var body: some View { RecipientPickerView(model: model, onDone: onDone, onFindFriends: onFindFriends) }
}

private struct SentPhotosScreen: View {
    @State private var model: SentPhotosViewModel

    init(environment: AppEnvironment) {
        _model = State(initialValue: SentPhotosViewModel(repository: environment.photos))
    }

    var body: some View { SentPhotosView(model: model) }
}

private struct OtherSettingsScreen: View {
    @State private var model: DeleteAccountViewModel
    let onAccountDeleted: () -> Void

    init(environment: AppEnvironment, onAccountDeleted: @escaping () -> Void) {
        _model = State(initialValue: DeleteAccountViewModel(users: environment.users))
        self.onAccountDeleted = onAccountDeleted
    }

    var body: some View { OtherSettingsView(model: model, onAccountDeleted: onAccountDeleted) }
}

private struct BlockedAccountsScreen: View {
    @State private var model: BlockedAccountsViewModel

    init(environment: AppEnvironment) {
        _model = State(initialValue: BlockedAccountsViewModel(repository: environment.safety))
    }

    var body: some View { BlockedAccountsView(model: model) }
}
