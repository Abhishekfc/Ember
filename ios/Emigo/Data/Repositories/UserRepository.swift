import Foundation

/// The signed-in user's own profile.
@MainActor
final class UserRepository {
    private let api: APIClient

    /// Home and Memories both ask for the profile every time they open; within the cache window the
    /// last answer is reused. Changing the name, username or photo stores the new profile at once.
    private let profileCache = TtlCache<String, UserProfile>()
    private let profileFlight = SingleFlight<String, UserProfile>()
    private static let profileKey = "me"

    init(api: APIClient) {
        self.api = api
    }

    /// Forgets the remembered profile. Called on sign-out.
    func clearCache() {
        profileCache.removeAll()
    }

    func myProfile(forceRefresh: Bool = false) async throws -> UserProfile {
        if !forceRefresh, let cached = profileCache.value(for: Self.profileKey) { return cached }
        let profile = try await profileFlight.run(forceRefresh ? "me-refresh" : Self.profileKey) { [api] in
            try await api.send(.myProfile())
        }
        profileCache.store(profile, for: Self.profileKey)
        return profile
    }

    func updateName(_ displayName: String) async throws -> UserProfile {
        remember(try await api.send(.updateProfile(displayName: displayName)))
    }

    func updateUsername(_ username: String) async throws -> UserProfile {
        remember(try await api.send(.updateProfile(username: username)))
    }

    private func remember(_ profile: UserProfile) -> UserProfile {
        profileCache.store(profile, for: Self.profileKey)
        return profile
    }

    /// For the edit-username screen, where the account (and its token) already exists.
    func usernameAvailability(_ username: String) async throws -> UsernameAvailability {
        try await api.send(.usernameAvailability(username))
    }

    func uploadProfilePhoto(jpeg: Data) async throws -> UserProfile {
        remember(try await api.send(.uploadProfilePhoto(jpeg: jpeg)))
    }

    /// Deletes the account, its photos and its friendships on the server. Can't be undone.
    func deleteAccount() async throws {
        _ = try await api.send(.deleteAccount())
    }
}
