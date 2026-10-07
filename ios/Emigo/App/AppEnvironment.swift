import Foundation

/// Everything the app's screens depend on, built once at launch and handed down. Screens never
/// create repositories or talk to Firebase themselves, which keeps them testable.
@MainActor
final class AppEnvironment {
    let identity: IdentityProvider
    let api: APIClient
    let sessionStore: SessionStore
    let auth: AuthRepository
    let users: UserRepository
    let photos: PhotoRepository
    let friends: FriendRepository
    let activity: ActivityRepository
    let safety: SafetyRepository
    let subscription: SubscriptionRepository
    /// Which theme the app is wearing.
    let themes: ThemeStore
    /// Photos waiting to be sent, kept on disk until they reach the server.
    let outbox: PendingSendQueue
    let imageLoader: ImageLoader

    init(
        identity: IdentityProvider,
        baseURL: URL,
        sessionStore: SessionStore = SessionStore(),
        urlSession: URLSession = APIClient.makeSession(),
        imageLoader: ImageLoader = .shared,
        outboxDirectory: URL = PendingSendQueue.defaultDirectory()
    ) {
        self.identity = identity
        self.sessionStore = sessionStore
        self.imageLoader = imageLoader
        let api = APIClient(baseURL: baseURL, tokenProvider: identity, session: urlSession)
        self.api = api
        self.auth = AuthRepository(api: api, identity: identity, store: sessionStore)
        self.users = UserRepository(api: api)
        let photos = PhotoRepository(api: api)
        self.photos = photos
        self.friends = FriendRepository(api: api)
        self.activity = ActivityRepository(api: api)
        self.safety = SafetyRepository(api: api)
        let subscription = SubscriptionRepository(api: api, store: sessionStore)
        self.subscription = subscription
        self.themes = ThemeStore(store: sessionStore, subscription: subscription)
        self.outbox = PendingSendQueue(directory: outboxDirectory, transport: photos)
    }

    /// The real app: Firebase sign-in (when this build has its config file) and the live server.
    static func live() -> AppEnvironment {
        let identity: IdentityProvider = FirebaseBootstrap.configureIfPossible()
            ? FirebaseIdentityProvider()
            : UnconfiguredIdentityProvider()
        return AppEnvironment(identity: identity, baseURL: AppConfiguration.apiBaseURL)
    }
}

/// Values that differ between Debug and Release builds, set in `Config/*.xcconfig`.
enum AppConfiguration {
    static var apiBaseURL: URL {
        guard let text = Bundle.main.object(forInfoDictionaryKey: "EmigoAPIBaseURL") as? String,
              let url = URL(string: text) else {
            preconditionFailure("EmigoAPIBaseURL is missing from Info.plist; check Config/*.xcconfig")
        }
        return url
    }

    /// Just the version number, such as "0.1.0".
    static var marketingVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
    }
}
